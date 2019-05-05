/*
 * SnmpClient.scala
 *
 * Updated: Dec 3, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle.akkasnmp4j.util

import java.net.{InetAddress, InetSocketAddress}
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import org.snmp4j.event.{ResponseEvent, ResponseListener}
import org.snmp4j.mp.MPv3
import org.snmp4j.security.{SecurityModels, SecurityProtocols, USM}
import org.snmp4j.smi.{OID, OctetString, Variable, VariableBinding}
import org.snmp4j.util.{DefaultPDUFactory, TableEvent, TableListener, TableUtils}
import org.snmp4j.{PDU, ScopedPDU, Snmp, Target ⇒ snmpTarget}

import com.codemettle.akkasnmp4j.config.{CredOptions, GetOptions}
import com.codemettle.akkasnmp4j.transport.udp.AkkaUdpTransport
import com.codemettle.akkasnmp4j.util.SnmpClient.FetchTableHandle
import com.codemettle.akkasnmp4j.util.SnmpClient.Messages.{TableFetchComplete, TableFetchNext}

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import scala.concurrent.{Future, Promise}

/**
 * @author steven
 *
 */
object SnmpClient {
    class FetchTableHandle(val fetchId: UUID, cancelled: AtomicBoolean) {
        def cancel(): Unit = cancelled set true
    }

    object Messages {
        sealed trait TableFetchEvent {
            def event: TableEvent

            def fetchId = event.getUserObject match {
                case id: UUID ⇒ id
                case _ ⇒ sys.error("unknown userObject")
            }

            def isError = event.isError
        }

        case class TableFetchNext(event: TableEvent) extends TableFetchEvent {
            def index = event.getIndex

            def column(oid: OID) = event.getColumns find (Option(_) exists (_.getOid startsWith oid))
        }

        case class TableFetchComplete(event: TableEvent) extends TableFetchEvent
    }

    def apply(session: Snmp)(implicit arf: ActorRefFactory): SnmpClient = new SnmpClient(session)
    def apply()(implicit arf: ActorRefFactory): SnmpClient = {
        val sess = new Snmp(new AkkaUdpTransport("UDP"))

        // Set up USM for SNMPv3
        val usm = new USM(SecurityProtocols.getInstance, new OctetString(MPv3.createLocalEngineID), 0)
        SecurityModels.getInstance.addSecurityModel(usm)

        sess.listen()
        apply(sess)
    }
}

class SnmpClient(val session: Snmp)(implicit arf: ActorRefFactory) {

    private def tableUtils(implicit options: GetOptions, credOpts: CredOptions): TableUtils = {
        val factory = new DefaultPDUFactory() {
            override def createPDU(target: snmpTarget): PDU = {
                val pdu = super.createPDU(target)

                pdu match {
                    case spdu: ScopedPDU ⇒ credOpts.contextName.map(new OctetString(_)).foreach(spdu.setContextName)
                    case _ ⇒
                }

                pdu
            }
        }

        new TableUtils(session, factory)
    }

    private def defGetOpts = GetOptions(actorSystem)
    private def defCredOpts = CredOptions(actorSystem)

    //private val logger = akka.event.Logging.getLogger(actorSystem, this)

    def send(addr: InetSocketAddress, pduUpd: (PDU) ⇒ Unit, vars: Seq[VariableBinding], forSet: Boolean)
            (implicit getOpts: GetOptions = defGetOpts, credOpts: CredOptions =  defCredOpts): Future[ResponseEvent] = {
        val p = Promise[ResponseEvent]()

        val pdu = credOpts.version match {
            case SnmpVersion.v3 ⇒
                val spdu = new ScopedPDU()
                credOpts.contextName.map(new OctetString(_)).foreach(spdu.setContextName)
                spdu

            case _ ⇒ new PDU()
        }

        pduUpd(pdu)
        vars.foreach(pdu.add)

        val s4jtarget = Target.createTarget(session, addr, getOpts, credOpts, forSet)

        session.send(pdu, s4jtarget, null, new ResponseListener {
            override def onResponse(event: ResponseEvent): Unit = {
                session.cancel(event.getRequest, this)

                p success event
            }
        })

        p.future
    }

    @deprecated("Use method that takes InetSocketAddress", "0.12.0")
    def get(addr: InetAddress, oids: OID*)
           (implicit getOpts: GetOptions = defGetOpts, credOpts: CredOptions =  defCredOpts): Future[ResponseEvent] =
        send(new InetSocketAddress(addr, getOpts.port), _.setType(PDU.GET), oids.map(new VariableBinding(_)), forSet = false)

    def get(addr: InetSocketAddress, oids: OID*)
           (implicit getOpts: GetOptions, credOpts: CredOptions): Future[ResponseEvent] =
        send(addr, _.setType(PDU.GET), oids.map(new VariableBinding(_)), forSet = false)

    def set(addr: InetSocketAddress, sets: (OID, Variable)*)
           (implicit getOpts: GetOptions, credOpts: CredOptions): Future[ResponseEvent] =
        send(addr, _.setType(PDU.SET), sets.map(e ⇒ new VariableBinding(e._1, e._2)), forSet = true)

    @deprecated("Use method that takes InetSocketAddress", "0.12.0")
    def fetchTable(addr: InetAddress, oids: OID*)
                  (implicit eventTarget: ActorRef, getOpts: GetOptions = defGetOpts, credOpts: CredOptions = defCredOpts): FetchTableHandle =
        fetchTable(new InetSocketAddress(addr, getOpts.port), oids: _*)

    def fetchTable(addr: InetSocketAddress, oids: OID*)
                  (implicit eventTarget: ActorRef, getOpts: GetOptions, credOpts: CredOptions): FetchTableHandle = {
        val s4jtarget = Target.createTarget(session, addr, getOpts, credOpts)

        val cancelled = new AtomicBoolean(false)
        val fetchId = UUID.randomUUID()

        val tl = new TableListener {
            override def next(event: TableEvent): Boolean = {
                eventTarget ! TableFetchNext(event)
                !cancelled.get()
            }

            override def isFinished: Boolean = false

            override def finished(event: TableEvent): Unit = {
                eventTarget ! TableFetchComplete(event)
            }
        }

        tableUtils.getTable(s4jtarget, oids.toArray, tl, fetchId, null, null)

        new FetchTableHandle(fetchId, cancelled)
    }

    @deprecated("Use method that takes InetSocketAddress", "0.12.0")
    def fetchTableRows(ipAddr: InetAddress, oids: OID*)
                      (implicit getOpts: GetOptions, credOpts: CredOptions): Future[(Vector[TableFetchNext], TableFetchComplete)] =
        fetchTableRows(new InetSocketAddress(ipAddr, getOpts.port), oids: _*)

    def fetchTableRows(addr: InetSocketAddress, oids: OID*)
                      (implicit getOpts: GetOptions, credOpts: CredOptions): Future[(Vector[TableFetchNext], TableFetchComplete)] = {
        val p = Promise[(Vector[TableFetchNext], TableFetchComplete)]()

        implicit val act = arf.actorOf(Props(new Actor {
            private var rows = Vector.empty[TableFetchNext]

            def receive = {
                case row: TableFetchNext ⇒ rows +:= row
                case done: TableFetchComplete ⇒
                    p success (rows → done)
                    context stop self
            }
        }))

        fetchTable(addr, oids: _*)

        p.future
    }
}
