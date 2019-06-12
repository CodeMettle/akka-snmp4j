/*
 * AkkaUdpTransport.scala
 *
 * Updated: Dec 8, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle.akkasnmp4j.transport.udp

import org.snmp4j.smi.{Address, UdpAddress}
import org.snmp4j.transport.TransportListener
import org.snmp4j.{TransportMapping, TransportStateReference}

import com.codemettle.akkasnmp4j.transport.TransportRequestTimeout
import com.codemettle.akkasnmp4j.transport.udp.UdpTransportActor.Messages
import com.codemettle.akkasnmp4j.util.Implicits._

import akka.actor._
import akka.pattern._
import akka.util.{ByteString, Timeout}
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}

/**
 * @author steven
 *
 */
class AkkaUdpTransport(udpAddr: UdpAddress, name: String)(implicit arf: ActorRefFactory)
    extends TransportMapping[UdpAddress] {
    import arf.dispatcher

    def this(name: String)(implicit arf: ActorRefFactory) = this(new UdpAddress(0), name)

    private val actor = arf.actorOf(UdpTransportActor.props(udpAddr, this), name)

    private implicit val timeout = Timeout(10.seconds)

    private def blockingRequest(call: Any, fromMethod: String) = {
        val resF = (actor ? call) transform(_ => (), {
            case _: AskTimeoutException => TransportRequestTimeout(fromMethod)
            case ex => ex
        })

        Await.result(resF, Duration.Inf)
    }

    override val getSupportedAddressClass: Class[_ <: Address] = classOf[UdpAddress]

    override def listen(): Unit = blockingRequest(Messages.StartListening, "listen")

    override def addTransportListener(transportListener: TransportListener): Unit = {
        blockingRequest(Messages.AddListener(transportListener), "addTransportListener")
    }

    override def isListening: Boolean = {
        val resF = (actor ? Messages.IsListening).mapTo[Boolean] recover {
            case _: AskTimeoutException => throw TransportRequestTimeout("isListening")
        }

        Await.result(resF, Duration.Inf)
    }

    override def removeTransportListener(transportListener: TransportListener): Unit = {
        blockingRequest(Messages.RemoveListener(transportListener), "removeTransportListener")
    }

    override val getListenAddress: UdpAddress = udpAddr

    override val getMaxInboundMessageSize: Int = Int.MaxValue

    override def close(): Unit = {
        val p = Promise[Unit]()

        arf.actorOf(Props(new Actor {
            override def preStart() = {
                super.preStart()

                context watch actor

                context setReceiveTimeout 10.seconds

                actor ! Messages.Stop
            }

            def receive = {
                case Terminated(`actor`) =>
                    p.success({})
                    context stop self

                case ReceiveTimeout =>
                    p failure TransportRequestTimeout("close")
                    context stop self
            }
        }))

        Await.result(p.future, Duration.Inf)
    }

    override def sendMessage(address: UdpAddress, message: Array[Byte],
                             tmStateReference: TransportStateReference): Unit = {
//        val resF = (actor ? Messages.SendMessage(ByteString(message), address)) map {
//            case Messages.Ack => ()
//            case Udp.CommandFailed => sys.error("send() failed")
//        } recover {
//            case _: AskTimeoutException => throw TransportRequestTimeout("sendMessage")
//        }

        //Await.result(resF, Duration.Inf)

        actor ! Messages.SendMessage(ByteString(message), address)
    }
}
