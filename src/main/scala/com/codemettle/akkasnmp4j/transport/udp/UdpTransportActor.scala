/*
 * UdpTransportActor.scala
 *
 * Updated: Dec 8, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle.akkasnmp4j
package transport.udp

import java.net.InetSocketAddress

import org.snmp4j.TransportStateReference
import org.snmp4j.security.SecurityLevel
import org.snmp4j.smi.UdpAddress
import org.snmp4j.transport.TransportListener

import com.codemettle.akkasnmp4j.transport.udp.UdpTransportActor.Messages._
import com.codemettle.akkasnmp4j.transport.udp.UdpTransportActor.{DispatchMessageReceived, MessageReceivedDispatcher, fsm}
import com.codemettle.akkasnmp4j.transport.{BindFailedException, TransportShuttingDownException}
import com.codemettle.akkasnmp4j.util.Implicits._

import akka.actor._
import akka.io.{IO, Udp}
import akka.util.{Helpers, ByteString}
import scala.util.control.Exception.ignoring

/**
 * @author steven
 *
 */
private[udp] object UdpTransportActor {
    def props(addr: InetSocketAddress, transport: AkkaUdpTransport) = {
        Props(new UdpTransportActor(addr, transport))
    }

    private[udp] object fsm {
        sealed trait State
        object Starting extends State
        object SimpleSender extends State
        object Binding extends State
        object SendReceive extends State
        object ShuttingDown extends State

        case class StateData(sendActor: ActorRef = null, sendRecvActor: ActorRef = null,
                             listeners: Set[TransportListener] = Set.empty, listenRequestor: Option[ActorRef] = None,
                             messageDispatcher: Option[ActorRef] = None) {
            def isListening = sendRecvActor != null
        }
    }

    object Messages {
        sealed trait UdpTransportMessage

        case class AddListener(list: TransportListener) extends UdpTransportMessage
        case class RemoveListener(list: TransportListener) extends UdpTransportMessage

        case object IsListening extends UdpTransportMessage
        case object StartListening extends UdpTransportMessage

        case class SendMessage(msg: ByteString, addr: InetSocketAddress)

        case object Stop extends UdpTransportMessage

        case object Ack extends Udp.Event
    }

    private class MessageReceivedDispatcher(transport: AkkaUdpTransport, boundAddress: UdpAddress) extends Actor {
        private val stateReference = new TransportStateReference(transport, boundAddress, null, SecurityLevel.undefined,
            SecurityLevel.undefined, false, null)

        def receive = {
            case DispatchMessageReceived(listeners, msg, fromAddr) ⇒
                listeners foreach (list ⇒ {
                    ignoring(classOf[Exception])(
                        list.processMessage(transport, fromAddr, msg.asByteBuffer, stateReference))
                })
        }
    }

    private object MessageReceivedDispatcher {
        def props(transport: AkkaUdpTransport, boundAddress: UdpAddress) = {
            Props(new MessageReceivedDispatcher(transport, boundAddress))
        }
    }

    private case class DispatchMessageReceived(listeners: Set[TransportListener], msg: ByteString,
                                               fromAddr: InetSocketAddress)

}

private[udp] class UdpTransportActor(bindAddress: InetSocketAddress, transport: AkkaUdpTransport)
    extends FSM[fsm.State, fsm.StateData] with Stash {
    startWith(fsm.Starting, fsm.StateData())

    private val msgDispatcherName = Iterator from 0 map (i ⇒ s"msgDispatcher${Helpers.base64(i)}")

    implicit val system: ActorSystem = util.actorSystem

    IO(Udp) ! Udp.SimpleSender

    whenUnhandled {
        case Event(IsListening, data) ⇒ stay() replying data.isListening
    }

    def handleListeners: StateFunction = {
        case Event(AddListener(list), data) ⇒ stay() using data.copy(listeners = data.listeners + list) replying Ack
        case Event(RemoveListener(list), data) ⇒ stay() using data.copy(listeners = data.listeners - list) replying Ack
    }

    when(fsm.Starting) (handleListeners orElse {
        case Event(Udp.SimpleSenderReady, data) ⇒
            log debug "Ready to send UDP packets"
            context watch sender()
            goto(fsm.SimpleSender) using data.copy(sendActor = sender())

        case _ ⇒
            stash()
            stay()
    })

    onTransition {
        case fsm.Starting -> fsm.SimpleSender ⇒ unstashAll()
    }

    when(fsm.SimpleSender) (handleListeners orElse {
        case Event(Stop, data) ⇒
            context unwatch data.sendActor
            data.sendActor ! PoisonPill
            stop()

        case Event(Terminated(dead), data) ⇒
            if (dead == data.sendActor) {
                IO(Udp) ! Udp.SimpleSender
                goto(fsm.Starting) using data.copy(sendActor = null)
            } else
                stay()

        case Event(SendMessage(msg, to), data) ⇒
            //data.sendActor forward Udp.Send(msg, to, Ack)
            data.sendActor ! Udp.Send(msg, to)
            stay()

        case Event(StartListening, data) ⇒
            IO(Udp) ! Udp.Bind(self, bindAddress)
            goto(fsm.Binding) using data.copy(listenRequestor = Some(sender()))
    })

    when(fsm.Binding) (handleListeners orElse {
        case Event(_: Udp.CommandFailed, data) ⇒
            log.error("Failed to bind to {}", bindAddress)
            data.listenRequestor foreach (_ ! Status.Failure(BindFailedException(bindAddress)))
            goto(fsm.SimpleSender) using data.copy(listenRequestor = None)

        case Event(Udp.Bound(localAddr), data) ⇒
            log.info("UDP bound to {}", localAddr)

            if (data.sendActor != null) {
                context unwatch data.sendActor
                data.sendActor ! PoisonPill
            }

            data.listenRequestor foreach (_ ! Ack)

            context watch sender()

            data.messageDispatcher foreach (_ ! PoisonPill)

            val msgDispatcher = context
                .actorOf(MessageReceivedDispatcher.props(transport, localAddr), msgDispatcherName.next())

            goto(fsm.SendReceive) using data.copy(sendActor = null, sendRecvActor = sender(), listenRequestor = None,
                messageDispatcher = Some(msgDispatcher))

        case _ ⇒
            stash()
            stay()
    })

    onTransition {
        case fsm.Binding -> _ ⇒ unstashAll()
    }

    when(fsm.SendReceive) (handleListeners orElse {
        case Event(Stop, data) ⇒
            log debug "Shutting down"
            goto(fsm.ShuttingDown) using data.copy()

        case Event(Terminated(dead), data) ⇒
            if (dead == data.sendRecvActor) {
                IO(Udp) ! Udp.Bind(self, bindAddress)
                goto(fsm.Binding) using data.copy(sendRecvActor = null)
            } else
                stay()

        case Event(SendMessage(msg, to), data) ⇒
            //data.sendRecvActor forward Udp.Send(msg, to, Ack)
            data.sendRecvActor ! Udp.Send(msg, to)
            stay()

        case Event(StartListening, _) ⇒ stay() replying Ack

        case Event(Udp.Received(msg, from), data) ⇒
            data.messageDispatcher foreach (_ ! DispatchMessageReceived(data.listeners, msg, from))

            stay()
    })

    onTransition {
        case fsm.SendReceive -> fsm.ShuttingDown ⇒
            context unwatch nextStateData.sendRecvActor
            nextStateData.sendRecvActor ! Udp.Unbind
    }

    when(fsm.ShuttingDown) (handleListeners orElse {
        case Event(Udp.Unbound, _) ⇒ stop()

        case Event(IsListening, _) ⇒ stay() replying true

        case Event(_, _) ⇒ stay() replying Status.Failure(TransportShuttingDownException())
    })

    initialize()
}
