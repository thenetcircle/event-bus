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

package com.thenetcircle.event_bus.story.interfaces

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.EventStatus.NORMAL
import com.thenetcircle.event_bus.story.{Payload, Story, TaskRunningContext}

trait ITask {

  private var taskName: Option[String] = None
  private var story: Option[Story]     = None

  def initTask(taskName: String, story: Story): Unit = initTask(taskName, Some(story))

  def initTask(taskName: String): Unit = initTask(taskName, None)

  def initTask(taskName: String, story: Option[Story]): Unit = {
    if (this.story.isDefined)
      throw new IllegalStateException(
        s"The task ${this.taskName} of story ${this.story.get.storyName} has been inited already."
      )
    this.taskName = Some(taskName)
    this.story = story
  }

  def getTaskName(): String     = this.taskName.getOrElse("unknown")
  def getStory(): Option[Story] = this.story
  def getStoryName(): String    = getStory().map(_.storyName).getOrElse("unknown")

  /**
    * Shutdown the task when something got wrong or the task has to be finished
    * It's a good place to clear up the resources like connection, actor, etc...
    * It's recommended to make this method to be idempotent, Because it could be called multiple times
    *
    * @param runningContext [[TaskRunningContext]]
    */
  def shutdown()(implicit runningContext: TaskRunningContext): Unit

}

object ITask {

  def wrapPartialFlow(
      partialFlow: Flow[Payload, Payload, NotUsed],
      decider: PartialFunction[Payload, Boolean] = {
        case (NORMAL, _) => true
      }
  )(implicit runningContext: TaskRunningContext): Flow[Payload, Payload, NotUsed] =
    Flow
      .fromGraph(
        GraphDSL
          .create() { implicit builder =>
            import GraphDSL.Implicits._

            // NORM goes to 0, Others goes to 1
            val partitioner = builder.add(
              new Partition[Payload](2, pl => if (decider.isDefinedAt(pl) && decider(pl)) 0 else 1, false)
            )

            val wrappedTaskFlow = Flow[Payload].via(partialFlow)
            val output          = builder.add(Merge[Payload](2))

            // format: off
            // ---------------  workflow graph start ----------------
            // NORM goes to taskHandler >>>
            partitioner.out(0)   ~>   wrappedTaskFlow   ~>   output.in(0)
            // Other status will skip this task flow >>>
            partitioner.out(1)              ~>               output.in(1)
            // ---------------  workflow graph end ----------------
            // format: on

            // ports
            FlowShape(partitioner.in, output.out)
          }
      )

}
