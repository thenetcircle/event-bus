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

package com.thenetcircle.event_bus

import com.thenetcircle.event_bus.misc.{Util, ZKManager}
import com.thenetcircle.event_bus.story._
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode
import org.apache.curator.framework.recipes.cache.{PathChildrenCache, PathChildrenCacheEvent}

import scala.collection.mutable
import scala.util.control.NonFatal

object ZKRunnerApp extends AbstractApp {
  // Setup Zookeeper
  if (args.length == 0 || args(0).isEmpty) {
    throw new RuntimeException("Argument 1 zookeeper connect string is required.")
  }
  val zookeeperConnectString = args(0)
  val zkManager: ZKManager =
    ZKManager.init(zookeeperConnectString, s"/event-bus/${appContext.getAppName()}")
  zkManager.registerStoryRunner(runnerName)

  val storyBuilder: StoryBuilder = StoryBuilder(TaskBuilderFactory(config))

  // Fetch stories and run
  import PathChildrenCacheEvent.Type._
  type Watcher = PathChildrenCache
  var watchedStores = mutable.Map.empty[String, (Watcher, Watcher)]
  // watching on zookeeper path to get updates of stories
  zkManager.watchChildren(s"runners/$runnerName/stories") { (runnerEvent, runnerWatcher) =>
    runnerEvent.getType match {

      // new story has been assigned to this runner
      case CHILD_ADDED =>
        val storyName = getLastPartOfPath(runnerEvent.getData.getPath)
        val storyRootPath = s"stories/$storyName"

        runStory(storyName)

        val storyWatcher =
          zkManager.watchChildren(storyRootPath, StartMode.BUILD_INITIAL_CACHE) {
            case (e, _) if e.getType == CHILD_UPDATED => updateStory(storyName)
            case _                                    =>
          }

        val storyTransformsWatcher =
          zkManager.watchChildren(s"$storyRootPath/transforms", StartMode.BUILD_INITIAL_CACHE) {
            case (e, _)
                if e.getType == CHILD_UPDATED || e.getType == CHILD_ADDED || e.getType == CHILD_REMOVED =>
              updateStory(storyName)
            case _ =>
          }

        watchedStores += (storyName -> (storyWatcher, storyTransformsWatcher))

      // story has been removed from this runner
      case CHILD_REMOVED =>
        val storyName = getLastPartOfPath(runnerEvent.getData.getPath)

        shutdownStory(storyName)

        watchedStores
          .get(storyName)
          .foreach {
            case (storyWatcher, storyTransformsWatcher) => {
              storyWatcher.close()
              storyTransformsWatcher.close()
            }
          }

      case _ =>
    }
  }

  def getStoryInfo(storyName: String): StoryInfo = {
    val storyRootPath = s"stories/$storyName"

    val status: String = zkManager.getData(s"$storyRootPath/status").getOrElse("INIT")
    val settings: String = zkManager.getData(s"$storyRootPath/settings").getOrElse("")
    val source: String = zkManager.getData(s"$storyRootPath/source").get
    val sink: String = zkManager.getData(s"$storyRootPath/sink").get
    val transforms: Option[List[String]] =
      zkManager.getChildrenData(s"$storyRootPath/transforms").map(m => m.values.toList)
    val fallback: Option[String] = zkManager.getData(s"$storyRootPath/fallback")

    StoryInfo(storyName, status, settings, source, sink, transforms, fallback)
  }

  def runStory(storyName: String): Unit = {
    try {
      val story = storyBuilder.buildStory(getStoryInfo(storyName))
      storyRunner ! StoryRunner.Run(story)
    } catch {
      case NonFatal(ex) =>
        logger.error(
          Util.colorfulOutput(s"fetching or building story failed with error $ex, when run story")
        )
    }
  }

  def updateStory(storyName: String): Unit = {
    try {
      val story = storyBuilder.buildStory(getStoryInfo(storyName))
      storyRunner ! StoryRunner.Rerun(story)
    } catch {
      case NonFatal(ex) =>
        logger.error(
          Util
            .colorfulOutput(s"fetching or building story failed with error $ex, when update story")
        )
    }
  }

  def shutdownStory(storyName: String): Unit = {
    storyRunner ! StoryRunner.Shutdown(Some(storyName))
  }

  def getLastPartOfPath(path: String): String =
    try {
      path.substring(path.lastIndexOf('/') + 1)
    } catch {
      case _: Throwable => ""
    }

}
