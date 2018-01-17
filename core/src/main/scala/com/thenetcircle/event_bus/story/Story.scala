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
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.interface._
import com.thenetcircle.event_bus.story.StoryStatus.StoryStatus
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.util.{Success, Try}

case class StorySettings(name: String, initStatus: StoryStatus = StoryStatus.INIT)

class Story(val settings: StorySettings,
            val sourceTask: SourceTask,
            val sinkTask: SinkTask,
            val transformTasks: Option[List[TransformTask]] = None,
            val fallbackTasks: Option[List[SinkTask]] = None)
    extends StrictLogging {

  import Story.M

  val storyName: String = settings.name

  private var status: StoryStatus = settings.initStatus
  def updateStatus(_status: StoryStatus): Unit = {
    status = _status
  }
  def getStatus(): StoryStatus = status

  private var flowId: Int = 0
  private def decorateFlow(
      flow: Flow[Event, M, NotUsed]
  )(implicit runningContext: TaskRunningContext): Flow[M, M, NotUsed] = {
    flowId = flowId + 1
    Story.decorateFlow(flow, s"story-$storyName-flow-$flowId", fallbackTasks)
  }

  def run()(implicit runningContext: TaskRunningContext): (KillSwitch, Future[Done]) = {
    val transforms =
      transformTasks
        .map(_.foldLeft(Flow[M]) { (_chain, _transform) =>
          _chain.via(decorateFlow(_transform.getHandler()))
        })
        .getOrElse(Flow[M])
    val sink = decorateFlow(sinkTask.getHandler())
    val handler = Flow[Event].map((Success(Done), _)).via(transforms).via(sink)

    sourceTask.runWith(handler.named(s"story-$storyName"))
  }
}

object Story extends StrictLogging {

  type M = (Try[Done], Event) // middle result type

  def decorateFlow(
      flow: Flow[Event, M, NotUsed],
      flowName: String,
      fallbackTasks: Option[List[SinkTask]] = None
  )(implicit runningContext: TaskRunningContext): Flow[M, M, NotUsed] = {

    Flow.fromGraph(
      GraphDSL
        .create() { implicit builder =>
          import GraphDSL.Implicits._

          val preCheck =
            builder.add(new Partition[M](2, input => if (input._1.isSuccess) 0 else 1))
          val postCheck =
            builder.add(Partition[M](2, input => if (input._1.isSuccess) 0 else 1))
          val output = builder.add(Merge[M](3))
          val logic = Flow[M].map(_._2).via(flow)

          // add other fallbacks
          val fallback = Flow[M]
            .map {
              case input @ (doneTry, event) =>
                val logMessage =
                  s"Event ${event.uniqueName} was processing failed on flow: $flowName." +
                    (if (fallbackTasks.isDefined) " Sending to fallbackTasks." else "")
                logger.warn(logMessage)
                input
            }
            .via(
              fallbackTasks
                .map(_list => {
                  Flow[M].map(_._2).via(_list.head.getHandler())
                })
                .getOrElse(Flow[M])
            )

          // workflow
          // format: off
          preCheck.out(0) ~> logic ~> postCheck
                                      postCheck.out(0)        ~>      output.in(0)
                                      postCheck.out(1) ~> fallback ~> output.in(1)
          preCheck.out(1)                       ~>                    output.in(2)
          // format: on

          // ports
          FlowShape(preCheck.in, output.out)
        }
    )
  }

}
