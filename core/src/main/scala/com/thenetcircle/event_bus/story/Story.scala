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
import com.thenetcircle.event_bus.interface.TaskSignal.{NoSignal, ToFallbackSignal}
import com.thenetcircle.event_bus.interface._
import com.thenetcircle.event_bus.story.StoryStatus.StoryStatus
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future

case class StorySettings(name: String, initStatus: StoryStatus = StoryStatus.INIT)

class Story(val settings: StorySettings,
            val sourceTask: SourceTask,
            val sinkTask: SinkTask,
            val transformTasks: Option[List[TransformTask]] = None,
            val fallbackTask: Option[FallbackTask] = None)
    extends StrictLogging {

  type MR = (TaskSignal, Event) // middle result type

  val storyName: String = settings.name

  private var status: StoryStatus = settings.initStatus
  def updateStatus(_status: StoryStatus): Unit = {
    status = _status
  }
  def getStatus(): StoryStatus = status

  def run()(implicit runningContext: TaskRunningContext): (KillSwitch, Future[Done]) = {

    val sourceHandler = wrapTask(Flow[MR], s"story-$storyName-source", skipPreCheck = true)

    var transformId = 0
    val transformsHandler =
      transformTasks
        .map(_.foldLeft(Flow[MR]) { (_chain, _transform) =>
          {
            transformId += 1
            _chain
              .via(
                wrapTask(
                  Flow[MR].map(_._2).via(_transform.getHandler()),
                  s"story-$storyName-transform-$transformId"
                )
              )
          }
        })
        .getOrElse(Flow[MR])

    val sinkHandler =
      wrapTask(Flow[MR].map(_._2).via(sinkTask.getHandler()), s"story-$storyName-sink")

    sourceTask.runWith(
      sourceHandler
        .via(transformsHandler)
        .via(sinkHandler)
    )

  }

  def wrapTask(taskHandler: Flow[MR, MR, NotUsed], taskName: String, skipPreCheck: Boolean = false)(
      implicit runningContext: TaskRunningContext
  ): Flow[MR, MR, NotUsed] = {

    Flow
      .fromGraph(
        GraphDSL
          .create() { implicit builder =>
            import GraphDSL.Implicits._

            // SkipPreCheck goes to 0, NoSignal goes to 0, Others goes to 1
            val preCheck =
              builder.add(new Partition[MR](2, input => {
                if (skipPreCheck) 0
                else {
                  input match {
                    case (NoSignal, _) => 0
                    case (_, _)        => 1
                  }
                }
              }))

            // ToFallbackSignal goes to 1, Others goes to 0
            val postCheck =
              builder.add(Partition[MR](2, {
                case (ToFallbackSignal, _) => 1
                case (_, _)                => 0
              }))

            val output = builder.add(Merge[MR](3))

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

            // format: off
            // ---------------  workflow graph start ----------------
            

            // no-signal goes to taskHandler >>>
            preCheck.out(0)   ~>   taskHandler   ~>   postCheck
                                                      // non to-fallback signal goes to next task
                                                      postCheck.out(0)            ~>              output.in(0)
                                                      // to-fallback signal goes to fallback  >>>
                                                      postCheck.out(1) ~>      fallback      ~>   output.in(1)

            // failure/skip/to-fallback signal goes to next task directly >>>
            preCheck.out(1)                                  ~>                                   output.in(2)


            // ---------------  workflow graph end ----------------
            // format: on

            // ports
            FlowShape(preCheck.in, output.out)
          }
      )
      .named(taskName)
  }
}
