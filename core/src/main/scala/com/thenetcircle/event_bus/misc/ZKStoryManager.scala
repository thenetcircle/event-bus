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

package com.thenetcircle.event_bus.misc
import akka.actor.{ActorRef, ActorSystem}
import com.thenetcircle.event_bus.context.AppContext
import com.thenetcircle.event_bus.story._
import com.typesafe.scalalogging.StrictLogging
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type._
import org.apache.curator.framework.recipes.cache.{
  ChildData,
  PathChildrenCache,
  PathChildrenCacheEvent
}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.matching.Regex

class ZKStoryManager(zkManager: ZKManager, runnerName: String, storyRunner: ActorRef)(
    implicit appContext: AppContext,
    system: ActorSystem
) extends StrictLogging {

  val storyBuilder: StoryBuilder = StoryBuilder(TaskBuilderFactory(appContext.getSystemConfig()))

  type ZEvent = PathChildrenCacheEvent
  type ZWatcher = PathChildrenCache

  var watchingStores = mutable.Map.empty[String, ZWatcher]

  zkManager.ensurePath(s"runners/$runnerName")

  val runnableStoriesPath = s"runners/$runnerName/stories"
  def getStoryRootPath(storyName: String): String = s"stories/$storyName"

  def runAndWatch(): Unit = {
    // watching on zookeeper path to get updates of stories
    zkManager.watchChildren(runnableStoriesPath, fetchData = false) { (re, rw) =>
      re.getType match {
        // new story has been assigned to this runner
        case CHILD_ADDED =>
          val storyName = getLastPartOfPath(re.getData.getPath)
          val storyWatcher =
            zkManager.watchChildren(getStoryRootPath(storyName), StartMode.POST_INITIALIZED_EVENT) {
              (se, sw) =>
                if (se.getType == INITIALIZED ||
                    se.getType == CHILD_UPDATED ||
                    // (se.getType == CHILD_ADDED && se.getInitialData != null) ||
                    se.getType == CHILD_REMOVED) {

                  val storyOption = createStory(storyName, sw.getCurrentData.asScala.toList)
                  storyOption.foreach(story => {
                    if (se.getType == INITIALIZED)
                      storyRunner ! StoryRunner.Run(story)
                    else
                      storyRunner ! StoryRunner.Rerun(story)
                  })
                }
            }
          watchingStores += (storyName -> storyWatcher)

        // story has been removed from this runner
        case CHILD_REMOVED =>
          val storyName = getLastPartOfPath(re.getData.getPath)
          logger.debug(s"removing story watcher of $storyName")
          watchingStores.get(storyName).foreach(_.close())
          storyRunner ! StoryRunner.Shutdown(Some(storyName))

        case _ =>
      }
    }
  }

  def createStory(storyName: String, data: List[ChildData]): Option[Story] = {
    try {
      val storyInfo =
        createStoryInfoFromZKData(storyName, data)
      logger.debug(s"new story info: $storyInfo")
      val story = storyBuilder.buildStory(storyInfo)
      logger.debug(s"new story ${story}")
      Some(story)
    } catch {
      case NonFatal(ex) =>
        logger.error(s"fetching or building story failed with error $ex, when run story")
        None
    }
  }

  def createStoryInfoFromZKData(storyName: String, data: List[ChildData]): StoryInfo = {
    val storyData = mutable.Map.empty[String, String]
    data.foreach(child => {
      storyData += (getLastPartOfPath(child.getPath) -> new String(child.getData, "UTF-8"))
    })

    val transforms: Option[List[String]] = storyData
      .get("transforms")
      .map(s => {
        s.split(Regex.quote("###")).toList
      })

    StoryInfo(
      storyName,
      storyData.getOrElse("status", "INIT"),
      storyData.getOrElse("settings", ""),
      storyData("source"),
      storyData("sink"),
      transforms,
      storyData.get("fallback")
    )
  }

  def getLastPartOfPath(path: String): String = {
    try {
      path.substring(path.lastIndexOf('/') + 1)
    } catch {
      case _: Throwable => ""
    }
  }
}
