/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Beineng Ma <baineng.ma@gmail.com>
 */

package com.thenetcircle.event_bus.story.tasks.operators

import java.util.concurrent.LinkedBlockingQueue

import akka.stream._
import akka.stream.scaladsl.BidiFlow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.thenetcircle.event_bus.AppContext
import com.thenetcircle.event_bus.event.EventStatus.TOFB
import com.thenetcircle.event_bus.misc.{Logging, Util}
import com.thenetcircle.event_bus.story.interfaces.{IBidiOperator, ITaskBuilder}
import com.thenetcircle.event_bus.story.{Payload, StoryMat, TaskRunningContext}
import com.typesafe.config.{Config, ConfigFactory}

case class FailoverBidiOperatorSettings(
    detachUpAndDown: Boolean = true,
    bufferSize: Int = 1
)

class FailoverBidiOperator(settings: FailoverBidiOperatorSettings) extends IBidiOperator with Logging {

  def divertToSecondarySink(pl: Payload): Payload = ???

  override def flow()(
      implicit runningContext: TaskRunningContext
  ): BidiFlow[Payload, Payload, Payload, Payload, StoryMat] =
    BidiFlow.fromGraph(new GraphStage[BidiShape[Payload, Payload, Payload, Payload]] {

      val in        = Inlet[Payload]("AsyncFailoverBidiOperator.in")
      val toOperate = Outlet[Payload]("AsyncFailoverBidiOperator.toOperate")
      val operated  = Inlet[Payload]("AsyncFailoverBidiOperator.operated")
      val out       = Outlet[Payload]("AsyncFailoverBidiOperator.out")

      val shape: BidiShape[Payload, Payload, Payload, Payload] =
        BidiShape.of(operated, out, in, toOperate)

      override def createLogic(
          inheritedAttributes: Attributes
      ): GraphStageLogic = new GraphStageLogic(shape) {

        private val buffer: java.util.Queue[Payload] = new LinkedBlockingQueue(settings.bufferSize)

        private def flushBuffer(): Unit =
          while (!buffer.isEmpty) {
            divertToSecondarySink(buffer.poll())
          }

        override def postStop(): Unit = flushBuffer()

        setHandler(
          in,
          new InHandler {
            override def onPush(): Unit = {
              val payload = grab(in)

              if (settings.detachUpAndDown) {
                if (isAvailable(out)) {
                  push(out, payload)
                }
              }

              if (buffer.isEmpty && isAvailable(toOperate)) {
                push(toOperate, payload)
              } else {
                if (!buffer.offer(payload)) { // if the buffer is full
                  producerLogger.warn(
                    s"A event is going to be sent to failover storage since the internal buffer is full. [${payload._1}] [" + Util
                      .getBriefOfEvent(payload._2) + "]"
                  )
                  divertToSecondarySink(payload)
                }
              }
            }

            override def onUpstreamFinish(): Unit =
              if (buffer.isEmpty) completeStage()
          }
        )

        setHandler(
          toOperate,
          new OutHandler {
            override def onPull(): Unit = {
              if (!buffer.isEmpty) push(out, buffer.poll())
              if (isClosed(in) && buffer.isEmpty) completeStage()
            }

            override def onDownstreamFinish(): Unit = {
              flushBuffer()
              super.onDownstreamFinish()
            }
          }
        )

        setHandler(
          operated,
          new InHandler {
            override def onPush(): Unit = {
              val payload = grab(in)
              val newPayload = payload match {
                case (TOFB(_, _), event) => divertToSecondarySink(payload)
                case _                   => payload
              }

              if (settings.detachUpAndDown) { // async
                pull(operated)
              } else { // sync
                if (isAvailable(out)) {
                  push(out, newPayload)
                }
              }
            }
          }
        )

        // outlet for outside
        setHandler(
          out,
          new OutHandler {
            override def onPull(): Unit =
              if (isClosed(in)) {
                if (buffer.isEmpty) completeStage()
              } else if (!hasBeenPulled(in)) {
                pull(in)
              }

            override def onDownstreamFinish(): Unit =
              if (buffer.isEmpty) completeStage()
          }
        )
      }
    })

}

object FailoverBidiOperator {

  trait FailoverStorage {
    def store(pl: Payload): Unit
  }

}

class FailoverBidiOperatorBuilder extends ITaskBuilder[FailoverBidiOperator] {

  override val taskType: String = "failover-bidi-operator"

  override val defaultConfig: Config =
    ConfigFactory.parseString(
      """{
        |}""".stripMargin
    )

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): FailoverBidiOperator = ???

}
