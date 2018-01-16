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
import com.thenetcircle.event_bus.misc.ZKManager
import com.thenetcircle.event_bus.story.StoryManager.StoryInfo

import scala.collection.mutable

class StoryManager(zkManager: ZKManager, taskBuilderFactory: TaskBuilderFactory)(
    implicit runningEnv: RunningEnvironment
) {

  lazy val runningContextFactory: TaskRunningContextFactory = TaskRunningContextFactory()
  implicit val system: ActorSystem = runningEnv.getActorSystem()

  def getAvailableStories(runnerGroup: String): List[String] = {
    zkManager
      .getChildren("stories")
      .map(_.filter(storyName => {
        zkManager
          .getData(s"stories/$storyName/runner-runnerGroup")
          .getOrElse("default") == runnerGroup
      }))
      .getOrElse(List.empty[String])
  }

  def getStoryInfo(storyName: String): StoryInfo = {
    val storyRootPath = s"stories/$storyName"

    val status: String = zkManager.getData(s"$storyRootPath/status").get
    val settings: String = zkManager.getData(s"$storyRootPath/settings").get
    val source: String = zkManager.getData(s"$storyRootPath/source").get
    val sink: String = zkManager.getData(s"$storyRootPath/sink").get
    val transforms: Option[List[String]] =
      zkManager.getChildrenData(s"$storyRootPath/transforms").map(_.map(_._2))
    val fallbacks: Option[List[String]] =
      zkManager.getChildrenData(s"$storyRootPath/fallbacks").map(_.map(_._2))

    StoryInfo(storyName, status, settings, source, sink, transforms, fallbacks)
  }

  def buildStory(storyName: String): Story = {
    val storyInfo: StoryInfo = getStoryInfo(storyName)
    new Story(
      StorySettings(storyName, StoryStatus(storyInfo.status)),
      taskBuilderFactory.buildSourceTask(storyInfo.source).get,
      taskBuilderFactory.buildSinkTask(storyInfo.sink).get,
      storyInfo.transforms.map(_.flatMap(_v => taskBuilderFactory.buildTransformTask(_v))),
      storyInfo.fallbacks.map(_.flatMap(_v => taskBuilderFactory.buildSinkTask(_v)))
    )
  }

  val runningStories: mutable.Map[String, ActorRef] = mutable.Map.empty
  def run(): Unit = {
    val candidateStories: List[String] = getAvailableStories(runningEnv.getRunnerGroup())

    candidateStories.foreach(storyName => {
      val story = buildStory(storyName)
      val storyRunner =
        system.actorOf(StoryRunner.props(runningContextFactory, story), storyName)
      runningStories += (storyName -> storyRunner)
    })
  }
}

object StoryManager {
  def apply(zkManager: ZKManager, taskBuilderFactory: TaskBuilderFactory)(
      implicit runningEnv: RunningEnvironment
  ): StoryManager =
    new StoryManager(zkManager, taskBuilderFactory)

  case class StoryInfo(name: String,
                       status: String,
                       settings: String,
                       source: String,
                       sink: String,
                       transforms: Option[List[String]],
                       fallbacks: Option[List[String]])
}
