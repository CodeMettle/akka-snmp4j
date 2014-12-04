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
import org.snmp4j.util.{TableEvent, TableListener, DefaultPDUFactory, TableUtils}
import org.snmp4j.{PDU, Snmp}
import spray.util.actorSystem

import com.codemettle.akkasnmp4j.config.GetOptions
import com.codemettle.akkasnmp4j.transport.udp.AkkaUdpTransport
import com.codemettle.akkasnmp4j.util.SnmpClient.{Messages, FetchTableHandle}

import akka.actor.{ActorRef, ActorRefFactory}
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
        case class TableFetchNext(event: TableEvent) {
            def index = event.getIndex

            def fetchId = event.getUserObject match {
                case id: UUID ⇒ id
                case _ ⇒ sys.error("unknown userObject")
            }

            def isError = event.isError

            def column(oid: OID) = event.getColumns find (_.getOid startsWith oid)
        }

        case class TableFetchComplete(fetchId: UUID)
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

    //private val logger = akka.event.Logging.getLogger(actorSystem, this)

    def get(target: CommunityTarget, oids: OID*)
           (implicit getOpts: GetOptions = GetOptions(actorSystem)): Future[ResponseEvent] = {
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
                  (implicit eventTarget: ActorRef, getOpts: GetOptions = GetOptions(actorSystem)): FetchTableHandle = {
        val s4jtarget = target.toSnmp4j
        s4jtarget setRetries getOpts.retries
        getOpts.timeout foreach (t ⇒ s4jtarget setTimeout t.toMillis)

        val cancelled = new AtomicBoolean(false)
        val fetchId = UUID.randomUUID()

        val tl = new TableListener {
            override def next(event: TableEvent): Boolean = {
                eventTarget ! Messages.TableFetchNext(event)
                !cancelled.get()
            }

            override def isFinished: Boolean = false

            override def finished(event: TableEvent): Unit = {
                eventTarget ! Messages.TableFetchComplete(fetchId)
            }
        }

        tableUtils.getTable(s4jtarget, oids.toArray, tl, fetchId, null, null)

        new FetchTableHandle(fetchId, cancelled)
    }
}
