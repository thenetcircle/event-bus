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
import com.thenetcircle.event_bus.story.{StoryBuilder, TaskBuilderFactory}
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent

class ZKManagerTest extends BaseTest {

  behavior of "ZKManager"

  it should "do proper initialization" in {

    val runnerName = "test-runner"

    val zkManager =
      ZKManager.init(
        "maggie-zoo-1:2181,maggie-zoo-2:2181",
        s"/event-bus/${appContext.getAppName()}"
      )

    zkManager.registerStoryRunner(runnerName)

    val storyDAO: StoryZookeeperDAO = StoryZookeeperDAO(zkManager)
    val storyBuilder: StoryBuilder = StoryBuilder(TaskBuilderFactory(appContext.getSystemConfig()))

    storyDAO
      .getRunnableStories(runnerName)
      .foreach(storyName => {

        /*val pathCache =
          new PathChildrenCache(
            zkManager.getClient(),
            s"/event-bus/${appContext.getAppName()}/stories/$storyName",
            false
          )

        pathCache.start()

        pathCache.getCurrentData.asScala
          .foreach(c => logger.debug(s"---- ${c.getData} -- ${c.getData} ----"))

        pathCache.getListenable.addListener(new PathChildrenCacheListener {
          override def childEvent(client: CuratorFramework, event: PathChildrenCacheEvent): Unit = {
            logger.debug(
              s"==== ${event.getType} -- ${event.getData.getData} --- ${event.getData.getPath} ===="
            )
          }
        })

        val treeCache =
          new TreeCache(
            zkManager.getClient(),
            s"/event-bus/${appContext.getAppName()}/stories/$storyName"
          )

        treeCache.start()

        treeCache.getListenable.addListener(new TreeCacheListener {
          override def childEvent(client: CuratorFramework, event: TreeCacheEvent): Unit = {
            logger.debug(
              s"==== ${event.getType} -- ${event.getData.getData} --- ${event.getData.getPath} ===="
            )
          }
        })*/

        /*zkManager.watchChildren(s"runners/$runnerName/stories") { event =>
          val path = event.getData.getPath
          val category = try {
            path.substring(path.lastIndexOf('/') + 1)
          } catch {
            case _: Throwable => ""
          }

          logger.debug(
            s"==== ${event.getType} -- ${new String(event.getData.getData, "UTF-8")} --- ${event.getData.getPath} --- $category ===="
          )

        }*/

        zkManager
          .watchChildren(s"stories/$storyName", startMode = StartMode.POST_INITIALIZED_EVENT) {
            (event, watcher) =>
              if (event.getType == PathChildrenCacheEvent.Type.INITIALIZED)
                /*{
                  import scala.collection.JavaConverters._
                  watcher.getCurrentData.asScala.foreach(childData => childData.)
                }*/
                logger.debug(s" --- type: ${event.getType}, data: ${watcher.getCurrentData}")
            /*val path = event.getData.getPath
              val category = try {
                path.substring(path.lastIndexOf('/') + 1)
              } catch {
                case _: Throwable => ""
              }

              logger.debug(
                s"==== ${event.getType} -- ${new String(event.getData.getData, "UTF-8")} --- ${event.getData.getPath} --- $category ===="
              )*/
          }

        /*logger.debug(s"get story name $storyName")

        val storyInfo = storyDAO.getStoryInfo(storyName)

        logger.debug(s"get story info $storyInfo")

        val story: Story = storyBuilder.buildStory(storyInfo)
        logger.debug(s"story settings ${story.settings}")
        logger.debug(s"source task ${story.sourceTask.asInstanceOf[HttpSource].settings}")
        logger.debug(s"sink task ${story.sinkTask.asInstanceOf[KafkaSink].settings}")
        story.transformTasks.foreach(
          _.foreach(
            ts => logger.debug(s"transform task ${ts.asInstanceOf[TNCKafkaTopicResolver].useCache}")
          )
        )
        logger
          .debug(
            s"fallback task ${story.fallbackTask.get.asInstanceOf[CassandraFallback].settings}"
          )*/

      })

    Thread.sleep(180000)

  }

}
