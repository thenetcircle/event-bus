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
import com.thenetcircle.event_bus.context.{TaskRunningContextFactory}
import com.thenetcircle.event_bus.helper.ZKManager
import com.thenetcircle.event_bus.story.StoryRunner.StoryInfo

import scala.collection.mutable

class StoryRunner(runnerName: String, zkManager: ZKManager, builderFactory: TaskBuilderFactory)(
    implicit system: ActorSystem
) {

  lazy val runningContextFactory: TaskRunningContextFactory = TaskRunningContextFactory()

  def getAvailableStories(runnerGroup: String): List[String] = {
    zkManager
      .getChildren("stories")
      .map(_.filter(storyName => {
        zkManager
          .getData(s"stories/$storyName/runner-group")
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
      builderFactory.buildSourceTask(storyInfo.source).get,
      builderFactory.buildSinkTask(storyInfo.sink).get,
      storyInfo.transforms.map(_.flatMap(_v => builderFactory.buildTransformTask(_v))),
      storyInfo.fallbacks.map(_.flatMap(_v => builderFactory.buildSinkTask(_v)))
    )
  }

  val runningStories: mutable.Map[String, ActorRef] = mutable.Map.empty
  def run(): Unit = {
    val candidateStories: List[String] = getAvailableStories(runnerName)

    candidateStories.foreach(storyName => {
      val story = buildStory(storyName)
      val storyWrapper =
        system.actorOf(StoryWrapper.props(runningContextFactory, story), storyName)
      runningStories += (storyName -> storyWrapper)
    })
  }
}

object StoryRunner {
  def apply(zkManager: ZKManager,
            builderFactory: TaskBuilderFactory)(implicit system: ActorSystem): StoryRunner =
    new StoryRunner(zkManager, builderFactory)

  case class StoryInfo(name: String,
                       status: String,
                       settings: String,
                       source: String,
                       sink: String,
                       transforms: Option[List[String]],
                       fallbacks: Option[List[String]])
}
