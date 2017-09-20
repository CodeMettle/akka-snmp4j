/*
 * SnmpClient.scala
 *
 * Updated: Dec 3, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle.akkasnmp4j.util

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import org.snmp4j.event.{ResponseEvent, ResponseListener}
import org.snmp4j.smi.{OID, VariableBinding}
import org.snmp4j.util.{DefaultPDUFactory, TableEvent, TableListener, TableUtils}
import org.snmp4j.{PDU, Snmp}

import com.codemettle.akkasnmp4j.config.GetOptions
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
        sess.listen()
        apply(sess)
    }
}

class SnmpClient(val session: Snmp)(implicit arf: ActorRefFactory) {

    lazy val tableUtils = new TableUtils(session, new DefaultPDUFactory())

    private def defGetOpts = GetOptions(actorSystem)

    //private val logger = akka.event.Logging.getLogger(actorSystem, this)

    def get(target: CommunityTarget, oids: OID*)
           (implicit getOpts: GetOptions = defGetOpts): Future[ResponseEvent] = {
        val p = Promise[ResponseEvent]()

        val pdu = new PDU()
        oids foreach (o ⇒ pdu add new VariableBinding(o))

        val s4jtarget = target.toSnmp4j
        s4jtarget setRetries getOpts.retries
        getOpts.timeout foreach (t ⇒ s4jtarget setTimeout t.toMillis)

        session.get(pdu, s4jtarget, null, new ResponseListener {
            override def onResponse(event: ResponseEvent): Unit = {
                session.cancel(event.getRequest, this)

                p success event
            }
        })

        p.future
    }

    def fetchTable(target: CommunityTarget, oids: OID*)
                  (implicit eventTarget: ActorRef, getOpts: GetOptions = defGetOpts): FetchTableHandle = {
        val s4jtarget = target.toSnmp4j
        s4jtarget setRetries getOpts.retries
        getOpts.timeout foreach (t ⇒ s4jtarget setTimeout t.toMillis)

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

    def fetchTableRows(target: CommunityTarget, oids: OID*)
                      (implicit getOpts: GetOptions = defGetOpts): Future[(Vector[TableFetchNext], TableFetchComplete)] = {
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

        fetchTable(target, oids: _*)

        p.future
    }
}
