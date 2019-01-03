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

package com.thenetcircle.event_bus.story

import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition}
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.context.TaskRunningContext
import com.thenetcircle.event_bus.interfaces.EventStatus.{NORM, TOFB}
import com.thenetcircle.event_bus.interfaces.{Event, _}
import com.thenetcircle.event_bus.misc.{Logging, MonitoringHelp}
import com.thenetcircle.event_bus.story.StoryStatus.StoryStatus

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

case class StorySettings(name: String, status: StoryStatus = StoryStatus.INIT)

class Story(
    val settings: StorySettings,
    val sourceTask: SourceTask,
    val sinkTask: SinkTask,
    val transformTasks: Option[List[TransformTask]] = None,
    val fallbackTask: Option[FallbackTask] = None
) extends Logging
    with MonitoringHelp {

  import com.thenetcircle.event_bus.story.Story.Payload

  val storyName: String = settings.name

  private var storyStatus: StoryStatus = settings.status
  def updateStoryStatus(status: StoryStatus): Unit =
    storyStatus = status
  def getStoryStatus(): StoryStatus = storyStatus

  private var runningFuture: Option[Future[Done]] = None
  def run()(implicit runningContext: TaskRunningContext): Future[Done] = runningFuture getOrElse {
    try {
      val sourceHandler = Story.wrapTask(Flow[Payload], s"story:$storyName:source", fallbackTask, skipPreCheck = true)

      var transformId = 0
      val transformsHandler =
        transformTasks
          .map(_.foldLeft(Flow[Payload]) { (_chain, _transform) =>
            {
              transformId += 1
              _chain
                .via(
                  Story.wrapTask(
                    Flow[Payload].map(_._2).via(_transform.prepare()),
                    s"story:$storyName:transform:$transformId",
                    fallbackTask
                  )
                )
            }
          })
          .getOrElse(Flow[Payload])

      val sinkHandler =
        Story.wrapTask(Flow[Payload].map(_._2).via(sinkTask.prepare()), s"story:$storyName:sink", fallbackTask)

      val monitorFlow = Flow[Payload]
        .map(payload => {
          getStoryMonitor(storyName).newEvent(payload._2).onProcessed(payload._1, payload._2)
          payload
        })
        .watchTermination() {
          case (mat, done) =>
            done.onComplete {
              case Success(_)  => getStoryMonitor(storyName).onCompleted()
              case Failure(ex) => getStoryMonitor(storyName).onTerminated(ex)
            }(runningContext.getExecutionContext())
            mat
        }

      runningFuture = Some(
        sourceTask.runWith(
          sourceHandler
            .via(transformsHandler)
            .via(sinkHandler)
            .via(monitorFlow)
        )
      )

      runningFuture.get
    } catch {
      case ex: Throwable =>
        logger.error(s"run story $storyName failed with error $ex")
        shutdown()
        throw ex
    }
  }

  def shutdown()(implicit runningContext: TaskRunningContext): Unit =
    try {
      logger.info(s"stopping story $storyName")
      runningFuture = None
      sourceTask.shutdown()
      transformTasks.foreach(_.foreach(_.shutdown()))
      fallbackTask.foreach(_.shutdown())
      sinkTask.shutdown()
    } catch {
      case NonFatal(ex) =>
        logger.error(s"get an error $ex when stopping story $storyName")
        throw ex
    }

}

object Story extends Logging {

  type Payload = (EventStatus, Event) // middle result type

  def wrapTask(
      taskHandler: Flow[Payload, Payload, NotUsed],
      taskName: String,
      fallbackTask: Option[FallbackTask] = None,
      skipPreCheck: Boolean = false
  )(implicit runningContext: TaskRunningContext): Flow[Payload, Payload, NotUsed] =
    Flow
      .fromGraph(
        GraphDSL
          .create() { implicit builder =>
            import GraphDSL.Implicits._

            // SkipPreCheck goes to 0, NORM goes to 0, Others goes to 1
            val preCheck =
              builder.add(new Partition[Payload](2, input => {
                if (skipPreCheck) 0
                else {
                  input match {
                    case (NORM, _) => 0
                    case (_, _)    => 1
                  }
                }
              }))

            // TOFB goes to 1, Others goes to 0
            val postCheck =
              builder.add(Partition[Payload](2, {
                case (_: TOFB, _) => 1
                case (_, _)       => 0
              }))

            val output = builder.add(Merge[Payload](3))

            val fallback = Flow[Payload]
              .map {
                case input @ (_, event) =>
                  val logMessage =
                    s"Event ${event.uuid} was processing failed on task: $taskName." +
                      (if (fallbackTask.isDefined) " Sending to fallbackTask." else "")
                  logger.warn(logMessage)
                  input
              }
              .via(
                fallbackTask
                  .map(_task => Flow[Payload].via(_task.prepareForTask(taskName)))
                  .getOrElse(Flow[Payload])
              )

            val finalTaskHandler: Flow[Payload, Payload, NotUsed] = if (runningContext.getAppContext().isDev()) {
              taskHandler.via(Flow[Payload].map(pl => {
                logger.debug(s"task $taskName has returned $pl")
                pl
              }))
            } else {
              taskHandler
            }

            // format: off
            // ---------------  workflow graph start ----------------


            // NORM goes to taskHandler >>>
            preCheck.out(0)   ~>   finalTaskHandler   ~>   postCheck
                                                           // non-TOFB goes to next task
                                                           postCheck.out(0)            ~>              output.in(0)
                                                           // TOFB goes to fallback  >>>
                                                           postCheck.out(1) ~>      fallback      ~>   output.in(1)

            // Other status will skip this task >>>
            preCheck.out(1)                                       ~>                                   output.in(2)


            // ---------------  workflow graph end ----------------
            // format: on

            // ports
            FlowShape(preCheck.in, output.out)
          }
      )
      .named(taskName)
}
