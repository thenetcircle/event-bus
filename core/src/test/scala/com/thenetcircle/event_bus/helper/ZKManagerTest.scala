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

package com.thenetcircle.event_bus.helper
import com.thenetcircle.event_bus.BaseTest
import com.thenetcircle.event_bus.story.{Story, StoryBuilder, StoryZookeeperDAO, TaskBuilderFactory}
import com.thenetcircle.event_bus.tasks.cassandra.CassandraFallback
import com.thenetcircle.event_bus.tasks.http.HttpSource
import com.thenetcircle.event_bus.tasks.kafka.KafkaSink

class ZKManagerTest extends BaseTest {

  behavior of "ZKManager"

  it should "do proper initialization" in {

    val runnerName = "test-runner"

    val zkManager =
      ZookeeperManager.init(
        "maggie-zoo-1:2181,maggie-zoo-2:2181",
        s"/event-bus/${appContext.getAppName()}"
      )

    zkManager.registerStoryRunner(runnerName)

    val storyDAO: StoryZookeeperDAO = StoryZookeeperDAO(zkManager)
    val storyBuilder: StoryBuilder = StoryBuilder(TaskBuilderFactory(appContext.getSystemConfig()))

    storyDAO
      .getRunnableStoryNames(runnerName)
      .foreach(storyName => {

        logger.debug(s"get story name $storyName")

        val storyInfo = storyDAO.getStoryInfo(storyName)

        logger.debug(s"get story info $storyInfo")

        val story: Story = storyBuilder.buildStory(storyInfo)
        logger.debug(s"story settings ${story.settings}")
        logger.debug(s"source task ${story.sourceTask.asInstanceOf[HttpSource].settings}")
        logger.debug(s"sink task ${story.sinkTask.asInstanceOf[KafkaSink].settings}")
        logger.debug(s"transform task ${story.transformTasks}")
        logger
          .debug(s"fallback task ${story.fallbackTask.asInstanceOf[CassandraFallback].settings}")

      })

    Thread.sleep(10000)

  }

}
