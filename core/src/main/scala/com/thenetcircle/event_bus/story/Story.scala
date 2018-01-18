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
import com.thenetcircle.event_bus.interface.TaskSignal.{ToFallbackSignal, NoSignal, SkipSignal}
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
            _chain
              .via(wrapTask(_transform.getHandler(), s"story-$storyName-transform-$transformId"))
          }
        })
        .getOrElse(Flow[MR])

    val sink = wrapTask(sinkTask.getHandler(), s"story-$storyName-sink")

    // internal logic
    val head =
      wrapTask(Flow[Event].map(e => (Success(NoSignal), e)), s"story-$storyName-head")
    val tail =
      wrapTask(Flow[Event].map(e => (Success(NoSignal), e)), s"story-$storyName-tail")

    val workflow = head.via(transforms).via(sink).via(tail)

    sourceTask.runWith(workflow)
  }

  def wrapTask(taskHandler: Flow[Event, MR, NotUsed], taskName: String)(
      implicit runningContext: TaskRunningContext
  ): Flow[MR, MR, NotUsed] = {

    Flow
      .fromGraph(
        GraphDSL
          .create() { implicit builder =>
            import GraphDSL.Implicits._

            // Success goes 0, Failure/SkipSignal goes 1, ToFallbackSignal goes 2
            val signalCheck =
              builder.add(new Partition[MR](3, {
                case (Success(ToFallbackSignal), _) => 2
                case (Success(SkipSignal), _)       => 1
                case (Success(_), _)                => 0
                case (Failure(_), _)                => 1
              }))

            // Success goes 0, Failure goes 1
            val resultCheck =
              builder.add(Partition[MR](2, input => if (input._1.isSuccess) 0 else 1))

            val output = builder.add(Merge[MR](4))
            val task = Flow[MR].map(_._2).via(taskHandler)

            val fallbackFlow = fallbackTask
              .map(_task => Flow[MR].via(_task.getHandler(taskName)))
              .getOrElse(Flow[MR].map(input => Success(SkipSignal) -> input._2))

            val failureFallback = Flow[MR]
              .map {
                case input @ (_, event) =>
                  val logMessage =
                    s"Event ${event.uniqueName} was processing failed on task: $taskName." +
                      (if (fallbackTask.isDefined) " Sending to fallbackTask." else "")
                  logger.warn(logMessage)
                  input
              }
              .via(fallbackFlow)

            val signalFallback = Flow[MR].via(fallbackFlow)

            // format: off
            // ---------------  workflow graph start ----------------
            

            // no signal goes to run this task >>>
            signalCheck.out(0)   ~>   task   ~>   resultCheck
                                                  // success result goes to next task
                                                  resultCheck.out(0)              ~>            output.in(0)
                                                  // failure result directly goes to fallback  >>>
                                                  resultCheck.out(1) ~>    failureFallback   ~> output.in(1)
            // failure/skip-signal events goes to next task directly >>>
            signalCheck.out(1)                                 ~>                               output.in(2)
            // to-fallback-signal goes to fallback directly >>>
            signalCheck.out(2)              ~>            signalFallback          ~>            output.in(3)

            
            // ---------------  workflow graph end ----------------
            // format: on

            // ports
            FlowShape(signalCheck.in, output.out)
          }
      )
      .named(taskName)
  }
}
