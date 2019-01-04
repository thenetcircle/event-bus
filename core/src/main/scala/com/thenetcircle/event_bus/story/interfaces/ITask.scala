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

import com.thenetcircle.event_bus.context.TaskRunningContext
import com.thenetcircle.event_bus.story.Story

trait ITask {

  private var taskName: Option[String] = None
  private var story: Option[Story]     = None

  def initTask(taskName: String, story: Story): Unit = {
    if (this.story.isDefined)
      throw new IllegalStateException(
        s"The task ${this.taskName} of story ${this.story.get.storyName} has been inited already."
      )
    this.taskName = Some(taskName);
    this.story = Some(story)
  }
  def getTaskName(): String     = this.taskName.getOrElse("unknown")
  def getStory(): Option[Story] = this.story

  /**
    * Shutdown the task when something got wrong or the task has to be finished
    * It's a good place to clear up the resources like connection, actor, etc...
    * It's recommended to make this method to be idempotent, Because it could be called multiple times
    *
    * @param runningContext [[TaskRunningContext]]
    */
  def shutdown()(implicit runningContext: TaskRunningContext): Unit = {}

}
