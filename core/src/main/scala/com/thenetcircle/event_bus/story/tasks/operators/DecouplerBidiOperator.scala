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
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import akka.stream._
import akka.stream.scaladsl.{BidiFlow, Sink, Source, SourceQueueWithComplete}
import akka.stream.stage._
import com.thenetcircle.event_bus.AppContext
import com.thenetcircle.event_bus.event.EventStatus.{FAILED, STAGED, STAGING}
import com.thenetcircle.event_bus.story.interfaces.{IBidiOperator, IFailoverTask, ITaskBuilder, ITaskLogging}
import com.thenetcircle.event_bus.story.{Payload, StoryMat, TaskRunningContext}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

case class DecouplerSettings(
    bufferSize: Int = 10000,
    terminateDelay: FiniteDuration = 10 minutes,
    secondarySink: Option[IFailoverTask] = None,
    secondarySinkBufferSize: Int = 1000
)

class DecouplerBidiOperator(val settings: DecouplerSettings) extends IBidiOperator with ITaskLogging {

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
        taskLogger.warn(
          s"A event is going to be dropped since there is no secondary sink. Status: ${payload._1}, Event: ${payload._2.summary}"
        )
        Future.successful(payload)
    }

  override def flow()(
      implicit runningContext: TaskRunningContext
  ): BidiFlow[Payload, Payload, Payload, Payload, StoryMat] = {

    if (runningSecondarySink.isEmpty) {
      runningSecondarySink = settings.secondarySink.map(ssink => {
        // init failover task
        ssink.initTask(getTaskName(), getStory())
        // materialize failover stream
        Source
          .queue[Payload](settings.secondarySinkBufferSize, OverflowStrategy.dropNew)
          .via(ssink.failoverFlow())
          .to(Sink.ignore)
          .run()(runningContext.getMaterializer())
      })
      runningSecondarySink.foreach(_.watchCompletion().onComplete(result => {
        taskLogger.info(
          s"The secondary sink of task ${getTaskName()} completed with result: $result"
        )
      })(runningContext.getExecutionContext()))
    }

    BidiFlow.fromGraph(new GraphStage[BidiShape[Payload, Payload, Payload, Payload]] {

      val in        = Inlet[Payload]("Decoupler.in")
      val toOperate = Outlet[Payload]("Decoupler.toOperate")
      val operated  = Inlet[Payload]("Decoupler.operated")
      val out       = Outlet[Payload]("Decoupler.out")

      val shape: BidiShape[Payload, Payload, Payload, Payload] =
        BidiShape.of(operated, out, in, toOperate)

      override def createLogic(
          inheritedAttributes: Attributes
      ): GraphStageLogic = new TimerGraphStageLogic(shape) {

        private val buffer: java.util.Queue[Payload]     = new LinkedBlockingQueue(settings.bufferSize)
        private val pendingToOperatePayloads: AtomicLong = new AtomicLong(0L)
        private val completing: AtomicBoolean            = new AtomicBoolean(false)

        private def flushBuffer(): Unit =
          while (!buffer.isEmpty) {
            val payload = buffer.poll()
            taskLogger.info(
              s"A event is going to be sent to the secondary sink by flushBuffer(). Status: ${payload._1}, Event: ${payload._2.summary}"
            )
            payload match {
              case (_: STAGING, _) => divertToSecondarySink(payload)
              case (status, event) =>
                divertToSecondarySink(
                  (
                    STAGING(
                      Some(
                        new RuntimeException(s"Flushing buffer, It's original status is $status")
                      ),
                      getTaskName()
                    ),
                    event
                  )
                )
            }

          }
        private def flushBufferAndCompleteStage(): Unit = {
          flushBuffer()
          completeStage()
        }
        private def tryCompleteStage(): Unit =
          if (!completing.get()) {
            if (buffer.isEmpty && pendingToOperatePayloads.get() == 0)
              completeStage()
            else {
              scheduleOnce(None, settings.terminateDelay)
              completing.set(true)
            }
          }
        override protected def onTimer(timerKey: Any): Unit =
          flushBufferAndCompleteStage()

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
                pendingToOperatePayloads.incrementAndGet()
              } else {
                if (!buffer.offer(payload)) { // if the buffer is full
                  taskLogger.info(
                    s"A event is going to be sent to the secondary sink since the internal buffer is full. Status: ${payload._1}, Event: ${payload._2.summary}"
                  )
                  divertToSecondarySink(
                    (
                      STAGING(
                        Some(
                          new RuntimeException(s"The internal buffer is full, It's original status is ${payload._1}")
                        ),
                        getTaskName()
                      ),
                      payload._2
                    )
                  )
                }
              }
            }

            override def onUpstreamFinish(): Unit =
              tryCompleteStage()
          }
        )

        setHandler(
          toOperate,
          new OutHandler {
            override def onPull(): Unit = {
              if (!buffer.isEmpty) {
                push(toOperate, buffer.poll())
                pendingToOperatePayloads.incrementAndGet()
              }
              if (isClosed(in)) tryCompleteStage()
            }

            override def onDownstreamFinish(): Unit =
              flushBufferAndCompleteStage()
          }
        )

        setHandler(
          operated,
          new InHandler {
            override def onPush(): Unit = {
              val payload = grab(operated)

              pendingToOperatePayloads.decrementAndGet()
              if (isClosed(in)) tryCompleteStage()

              payload match {
                case (_: STAGING, _) =>
                  taskLogger.info(
                    s"A event is going to be sent to the secondary sink since it operated failed. Status: ${payload._1}, Event: ${payload._2.summary}"
                  )
                  divertToSecondarySink(payload) // make it async without blocking thread
                case _ =>
              }

              pull(operated)
            }

            override def onUpstreamFinish(): Unit =
              flushBufferAndCompleteStage()
          }
        )

        // outlet for outside
        setHandler(
          out,
          new OutHandler {
            override def onPull(): Unit =
              if (isClosed(in)) {
                tryCompleteStage()
              } else if (!hasBeenPulled(in)) {
                pull(in)
              }

            override def onDownstreamFinish(): Unit =
              tryCompleteStage()
          }
        )
      }
    })
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    runningSecondarySink.foreach(_.complete())
    settings.secondarySink.foreach(_.shutdown())
  }
}

class DecouplerBidiOperatorBuilder extends ITaskBuilder[DecouplerBidiOperator] {

  override val taskType: String = "decoupler"

  override val defaultConfig: Config =
    ConfigFactory.parseString("""{
      |  "buffer-size": 10000,
      |  "terminate-delay": "10 m",
      |  "secondary-sink-buffer-size": 1000
      |}""".stripMargin)

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): DecouplerBidiOperator = {
    val settings = DecouplerSettings(
      bufferSize = config.as[Int]("buffer-size"),
      terminateDelay = config.as[FiniteDuration]("terminate-delay"),
      secondarySink = config
        .as[Option[String]]("secondary-sink")
        .flatMap(content => Try(storyBuilder.map(_.buildFailoverTask(content))).getOrElse(None)),
      secondarySinkBufferSize = config.as[Int]("secondary-sink-buffer-size")
    )

    new DecouplerBidiOperator(settings)
  }

}
