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

import com.thenetcircle.event_bus.misc.ZKManager
import com.thenetcircle.event_bus.story.StoryDAO.StoryInfo
import org.apache.curator.framework.recipes.leader.LeaderLatch

class StoryDAO(zKManager: ZKManager) {

  def getAvailableStories(executorGroup: String): List[String] = {
    zKManager
      .getChildren("stories")
      .map(_.filter(storyName => {
        zKManager
          .getData(s"stories/$storyName/assigned-executor-group")
          .getOrElse("default") == executorGroup
      }))
      .getOrElse(List.empty[String])
  }

  def getStoryInfo(storyName: String): StoryInfo = {
    val storyRootPath = s"stories/$storyName"

    val status: String = zKManager.getData(s"$storyRootPath/status").get
    val settings: String = zKManager.getData(s"$storyRootPath/settings").get
    val source: String = zKManager.getData(s"$storyRootPath/source").get
    val sink: String = zKManager.getData(s"$storyRootPath/sink").get
    val transforms: Option[List[String]] =
      zKManager.getChildrenData(s"$storyRootPath/transforms").map(_.map(_._2))
    val fallbacks: Option[List[String]] =
      zKManager.getChildrenData(s"$storyRootPath/fallbacks").map(_.map(_._2))

    StoryInfo(storyName, status, settings, source, sink, transforms, fallbacks)
  }

  def hasLeadership(storyName: String): Boolean = {

    val leaderLatch = new LeaderLatch(zKManager.client, )

  }

}

object StoryDAO {
  def apply(zKManager: ZKManager): StoryDAO = new StoryDAO(zKManager)

  case class StoryInfo(name: String,
                       status: String,
                       settings: String,
                       source: String,
                       sink: String,
                       transforms: Option[List[String]],
                       fallbacks: Option[List[String]])
}
