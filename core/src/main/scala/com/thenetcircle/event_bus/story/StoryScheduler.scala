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

import akka.actor.{ActorRef, ActorSystem}

import scala.collection.mutable

class StoryScheduler(storyManager: StoryManager)(implicit environment: RunningEnvironment) {

  val taskRunningContextFactory: TaskRunningContextFactory = TaskRunningContextFactory()

  implicit val system: ActorSystem = environment.getActorSystem()
  private val storyRunners: mutable.Set[ActorRef] = mutable.Set.empty

  def execute(): Unit = {
    val candidateStories: List[String] =
      storyManager.getAvailableStories(environment.getRunnerGroup())

    candidateStories.foreach(storyName => {
      implicit val taskRunningContext: TaskRunningContext =
        taskRunningContextFactory.createTaskRunningContext()

      val story = storyManager.buildStory(storyName)
      val storyRunner = system.actorOf(StoryRunner.props(story), storyName)

      storyRunners += storyRunner
    })
  }

}

object StoryScheduler {
  def apply(storyManager: StoryManager)(implicit environment: RunningEnvironment): StoryScheduler =
    new StoryScheduler(storyManager)
}
