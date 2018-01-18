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
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.interface.TaskSignal.SkipOthers
import com.thenetcircle.event_bus.interface._
import com.thenetcircle.event_bus.story.StoryStatus.StoryStatus
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

case class StorySettings(name: String, initStatus: StoryStatus = StoryStatus.INIT)

class Story(val settings: StorySettings,
            val sourceTask: SourceTask,
            val sinkTask: SinkTask,
            val transformTasks: Option[List[TransformTask]] = None,
            val fallbackTask: Option[FallbackTask] = None)
    extends StrictLogging {

  type MR = (Try[TaskSignal], Event) // middle result type

  val storyName: String = settings.name

  private var status: StoryStatus = settings.initStatus
  def updateStatus(_status: StoryStatus): Unit = {
    status = _status
  }
  def getStatus(): StoryStatus = status

  def run()(implicit runningContext: TaskRunningContext): (KillSwitch, Future[Done]) = {
    var transformId = 0
    val transforms =
      transformTasks
        .map(_.foldLeft(Flow[MR]) { (_chain, _transform) =>
          {
            transformId += 1
            _chain.via(
              wrapTaskHandler(_transform.getHandler(), s"story-$storyName-transform-$transformId")
            )
          }
        })
        .getOrElse(Flow[MR])

    val sink = wrapTaskHandler(sinkTask.getHandler(), s"story-$storyName-sink")

    // TODO: change here
    val sourceResultHandler =
      wrapTaskHandler(Flow[Event].map(Task.makeNoResultEvent), s"story-$storyName-sourceresult")

    sourceTask.runWith(sourceResultHandler.via(transforms).via(sink))
  }

  def wrapTaskHandler(taskHandler: Flow[Event, MR, NotUsed], taskName: String)(
      implicit runningContext: TaskRunningContext
  ): Flow[MR, MR, NotUsed] = {

    Flow
      .fromGraph(
        GraphDSL
          .create() { implicit builder =>
            import GraphDSL.Implicits._

            // Success goes 0, Failure/SkipOthers goes 1
            val preCheck =
              builder.add(new Partition[MR](2, {
                case (Success(SkipOthers), _) => 1
                case (Success(_), _)          => 0
                case (Failure(_), _)          => 1
              }))

            // Success goes 0, Failure goes 1
            val postCheck =
              builder.add(Partition[MR](2, input => if (input._1.isSuccess) 0 else 1))

            val output = builder.add(Merge[MR](3))
            val logic = Flow[MR].map(_._2).via(taskHandler)

            // add other fallback
            val fallback = Flow[MR]
              .map {
                case input @ (_, event) =>
                  val logMessage =
                    s"Event ${event.uniqueName} was processing failed on task: $taskName." +
                      (if (fallbackTask.isDefined) " Sending to fallbackTask." else "")
                  logger.warn(logMessage)
                  input
              }
              .via(
                fallbackTask
                  .map(_task => Flow[MR].via(_task.getHandler(taskName)))
                  .getOrElse(Flow[MR])
              )

            // workflow
            // format: off
            
            // success flow >>>
            preCheck.out(0) ~> logic ~> postCheck
                                        postCheck.out(0)        ~>      output.in(0)
                                        // fallback flow >>>
                                        postCheck.out(1) ~> fallback ~> output.in(1)
            // failure/skip-others flow >>>
            preCheck.out(1)                       ~>                    output.in(2)
            
            // format: on

            // ports
            FlowShape(preCheck.in, output.out)
          }
      )
      .named(taskName)
  }
}
