package com.codemettle.akkasnmp4j.util

import org.snmp4j.Target
import org.snmp4j.smi.OID
import org.snmp4j.util.{TreeEvent, TreeListener, TreeUtils}

import akka.actor.Cancellable
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}
import scala.collection.mutable

class TreeEventGraph(treeUtils: TreeUtils, target: Target, rootOids: OID*) extends GraphStageWithMaterializedValue[SourceShape[TreeEvent], Cancellable] {
  import java.util.concurrent.atomic.AtomicBoolean

  val out: Outlet[TreeEvent] = Outlet("TreeEventSource")

  override val shape: SourceShape[TreeEvent] = SourceShape(out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Cancellable) = {
    val logic: GraphStageLogic with Cancellable = new GraphStageLogic(shape) with Cancellable {
      private val eventQueue = mutable.Queue.empty[TreeEvent]
      private val isDone = new AtomicBoolean(false)
      private var shouldPush = false

      private def nextElem(event: TreeEvent): Boolean =
        if (isDone.get) false
        else if (event.isError) {
          failStage(event.getException)
          false
        } else if (shouldPush) {
          push(out, event)
          shouldPush = false
          true
        } else {
          eventQueue.enqueue(event)
          true
        }

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit =
            if (eventQueue.nonEmpty) {
              push(out, eventQueue.dequeue())
            } else {
              shouldPush = true
            }

          override def onDownstreamFinish(): Unit = {
            super.onDownstreamFinish()
            isDone.getAndSet(true)
          }
        }
      )
      override def preStart(): Unit = {
        val acb = createAsyncCallback[TreeEvent](event => nextElem(event))

        treeUtils.walk(
          target,
          rootOids.toArray,
          null,
          new TreeListener {
            def isFinished: Boolean = isDone.get

            override def next(event: TreeEvent): Boolean = {
              acb.invoke(event)
              !isDone.get
            }

            override def finished(event: TreeEvent): Unit =
              createAsyncCallback[TreeEvent](event => {
                nextElem(event)
                isDone.getAndSet(true)
                completeStage()
              }).invoke(event)
          }
        )
      }

      override def cancel(): Boolean = {
        isDone.getAndSet(true)
        true
      }

      override def isCancelled: Boolean = isDone.get
    }
    (logic, logic)
  }
}
