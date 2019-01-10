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
import akka.stream.scaladsl.{BidiFlow, Sink, Source, SourceQueueWithComplete}
import akka.stream.stage._
import com.thenetcircle.event_bus.AppContext
import com.thenetcircle.event_bus.event.EventStatus.{FAILED, STAGED, STAGING}
import com.thenetcircle.event_bus.misc.{Logging, Util}
import com.thenetcircle.event_bus.story.interfaces.{IBidiOperator, IStageableTask, ITaskBuilder}
import com.thenetcircle.event_bus.story.{Payload, StoryMat, TaskRunningContext}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

import scala.concurrent.Future
import scala.concurrent.duration._

case class FailoverBidiOperatorSettings(
    bufferSize: Int = 1,
    bufferFlushDelay: FiniteDuration = 10 minutes,
    detachUpAndDown: Boolean = true,
    secondarySink: Option[IStageableTask] = None,
    secondarySinkBufferSize: Int = 10
)

class FailoverBidiOperator(settings: FailoverBidiOperatorSettings) extends IBidiOperator with Logging {

  var runningSecondarySink: Option[SourceQueueWithComplete[Payload]] = None

  def divertToSecondarySink(payload: Payload)(implicit runningContext: TaskRunningContext): Future[Payload] =
    runningSecondarySink match {
      case Some(rss) =>
        rss
          .offer(payload)
          .map {
            case QueueOfferResult.Enqueued => (STAGED, payload._2)
            case _ =>
              (FAILED(new RuntimeException("Sending the event to secondary sink failed"), getTaskName()), payload._2)
          }(runningContext.getExecutionContext())
      case None =>
        producerLogger.warn(
          s"A event is going to be dropped since there is no secondary sink. Status: ${payload._1}, Event: ${Util
            .getBriefOfEvent(payload._2)}"
        )
        Future.successful(payload)
    }

  override def flow()(
      implicit runningContext: TaskRunningContext
  ): BidiFlow[Payload, Payload, Payload, Payload, StoryMat] = {

    if (runningSecondarySink.isEmpty) {
      runningSecondarySink = settings.secondarySink.map(ssink => {
        Source
          .queue[Payload](settings.secondarySinkBufferSize, OverflowStrategy.dropNew)
          .via(ssink.flow())
          .to(Sink.ignore)
          .run()(runningContext.getMaterializer())
      })
      runningSecondarySink.foreach(_.watchCompletion().onComplete(result => {
        producerLogger.info(s"The secondary sink of task ${getTaskName()} completed with result: $result")
      })(runningContext.getExecutionContext()))
    }

    BidiFlow.fromGraph(new GraphStage[BidiShape[Payload, Payload, Payload, Payload]] {

      val in        = Inlet[Payload]("AsyncFailoverBidiOperator.in")
      val toOperate = Outlet[Payload]("AsyncFailoverBidiOperator.toOperate")
      val operated  = Inlet[Payload]("AsyncFailoverBidiOperator.operated")
      val out       = Outlet[Payload]("AsyncFailoverBidiOperator.out")

      val shape: BidiShape[Payload, Payload, Payload, Payload] =
        BidiShape.of(operated, out, in, toOperate)

      override def createLogic(
          inheritedAttributes: Attributes
      ): GraphStageLogic = new TimerGraphStageLogic(shape) {

        private val buffer: java.util.Queue[Payload] = new LinkedBlockingQueue(settings.bufferSize)

        private def flushBuffer(): Unit =
          while (!buffer.isEmpty) {
            val payload = buffer.poll()
            producerLogger.info(
              s"A event is going to be sent to the secondary sink by flushBuffer(). Status: ${payload._1}, Event: ${Util
                .getBriefOfEvent(payload._2)}"
            )
            divertToSecondarySink(payload)
          }

        private def scheduleCompleteStage(): Unit = scheduleOnce(None, settings.bufferFlushDelay)
        override protected def onTimer(timerKey: Any): Unit = {
          flushBuffer()
          completeStage()
        }

        override def preStart(): Unit = pull(operated)

        override def postStop(): Unit =
          flushBuffer()

        setHandler(
          in,
          new InHandler {
            override def onPush(): Unit = {
              val payload = grab(in)

              if (isAvailable(out)) {
                push(out, payload)
              }

              if (buffer.isEmpty && isAvailable(toOperate)) {
                push(toOperate, payload)
              } else {
                if (!buffer.offer(payload)) { // if the buffer is full
                  producerLogger.info(
                    s"A event is going to be sent to the secondary sink since the internal buffer is full. Status: ${payload._1}, Event: ${Util
                      .getBriefOfEvent(payload._2)}"
                  )
                  divertToSecondarySink(payload)
                }
              }
            }

            override def onUpstreamFinish(): Unit =
              if (buffer.isEmpty) completeStage()
              else scheduleCompleteStage()
          }
        )

        setHandler(
          toOperate,
          new OutHandler {
            override def onPull(): Unit = {
              if (!buffer.isEmpty) push(toOperate, buffer.poll())
              if (isClosed(in) && buffer.isEmpty) completeStage()
            }

            override def onDownstreamFinish(): Unit = {
              flushBuffer()
              completeStage()
            }
          }
        )

        setHandler(
          operated,
          new InHandler {
            override def onPush(): Unit = {
              val payload = grab(operated)

              payload match {
                case (_: STAGING, _) =>
                  producerLogger.info(
                    s"A event is going to be sent to the secondary sink since it operated failed. Status: ${payload._1}, Event: ${Util
                      .getBriefOfEvent(payload._2)}"
                  )
                  divertToSecondarySink(payload) // make it async without blocking thread
                case _ =>
              }

              pull(operated)
            }

            override def onUpstreamFinish(): Unit = {
              flushBuffer()
              completeStage()
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
              else scheduleCompleteStage()
          }
        )
      }
    })
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit =
    runningSecondarySink.foreach(_.complete())
}

class FailoverBidiOperatorBuilder extends ITaskBuilder[FailoverBidiOperator] {

  override val taskType: String = "failover-bidi-operator"

  override val defaultConfig: Config =
    ConfigFactory.parseString("""{
      |  "buffer-size": 1,
      |  "detach-up-and-down": true
      |}""".stripMargin)

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): FailoverBidiOperator = {
    val settings = FailoverBidiOperatorSettings(
      bufferSize = config.as[Int]("buffer-size"),
      detachUpAndDown = config.as[Boolean]("detach-up-and-down")
    )

    new FailoverBidiOperator(settings)
  }

}
