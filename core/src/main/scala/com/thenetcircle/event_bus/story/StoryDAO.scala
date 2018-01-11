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

class StoryDAO(zKManager: ZKManager) {

  def getAvailableStories(executorGroup: String): List[StoryInfo] = {
    zKManager
      .getChildren("stories")
      .map(_.filter(storyName => {
        zKManager
          .getData(s"stories/$storyName/assigned-executor-group")
          .getOrElse("default") == executorGroup
      }).map(getStoryInfo))
      .getOrElse(List.empty[StoryInfo])
  }

  def getStoryInfo(storyName: String): StoryInfo = {
    val storyRootPath = s"stories/$storyName"
    val taskA: String = zKManager.getData(s"$storyRootPath/A").get
    val settings: Option[String] = zKManager.getData(s"$storyRootPath/settings")
    val taskB: Option[String] = zKManager.getData(s"$storyRootPath/B")
    val taskC: Option[String] = zKManager.getData(s"$storyRootPath/C")
    val fallbacks: Option[String] = zKManager.getData(s"$storyRootPath/fallbacks")
    val status: String = zKManager.getData(s"$storyRootPath/status").get

    StoryInfo(storyName, taskA, status, taskB, taskC, fallbacks, settings)
  }

}

object StoryDAO {
  def apply(zKManager: ZKManager): StoryDAO = new StoryDAO(zKManager)

  case class StoryInfo(name: String,
                       taskA: String,
                       status: String,
                       taskB: Option[String],
                       taskC: Option[String],
                       fallbacks: Option[String],
                       settings: Option[String])
}
