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
import com.thenetcircle.event_bus.interface.TaskA
import com.thenetcircle.event_bus.misc.{ConfigStringParser, ZKManager}

class StoryDAO(zKManager: ZKManager, builderFactory: TaskBuilderFactory) {

  def fetchAvailableStories(executorGroup: String): List[Story] = {
    zKManager
      .getChildren("stories")
      .map(_.filter(storyName => {
        zKManager
          .getData(s"stories/$storyName/assigned-executor-group")
          .getOrElse("default") == executorGroup
      }).map(fetchStory))
      .getOrElse(List.empty[Story])
  }

  def fetchStory(storyName: String): Story = {
    val storyRootPath = s"stories/$storyName"

    val taskA: TaskA = zKManager.getData(s"$storyRootPath/A").flatMap(configString => {
      val _config = ConfigStringParser.convertStringToConfig(configString).getStringList("")
      builderFactory.buildTaskA(_config(0), _config(1))
    }).get

    val taskAConfigString: Option[String] = zKManager.getData(s"$storyRootPath/A")
    if (taskAConfigString.isEmpty) {
      throw new IllegalArgumentException(s"TaskA of Story $storyName does not exists.")
    }
    val taskA = builderFactory.buildTaskA()

    val taskB: Option[String] = zKManager.getData(s"$storyRootPath/B")
    val taskC: Option[String] = zKManager.getData(s"$storyRootPath/C")
    val fallbacks: Option[String] = zKManager.getData(s"$storyRootPath/fallbacks")

    new Story(storyName, new StorySettings(), )
  }

}

object StoryDAO {
  def apply(zKManager: ZKManager): StoryDAO = new StoryDAO(zKManager)
}
