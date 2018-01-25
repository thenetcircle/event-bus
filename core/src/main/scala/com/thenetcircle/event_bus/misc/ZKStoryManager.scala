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
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, ActorSystem}
import com.thenetcircle.event_bus.context.AppContext
import com.thenetcircle.event_bus.story._
import com.typesafe.scalalogging.StrictLogging
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type._
import org.apache.curator.framework.recipes.cache.{ChildData, PathChildrenCache, PathChildrenCacheEvent}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.control.NonFatal

class ZKStoryManager(zkManager: ZooKeeperManager, runnerName: String, storyRunner: ActorRef)(
    implicit appContext: AppContext,
    system: ActorSystem
) extends StrictLogging {

  val storyBuilder: StoryBuilder = StoryBuilder(TaskBuilderFactory(appContext.getSystemConfig()))

  type ZEvent   = PathChildrenCacheEvent
  type ZWatcher = PathChildrenCache

  val watchingStores = mutable.Map.empty[String, ZWatcher]

  zkManager.ensurePath(s"runners/$runnerName")

  def getStoryRootPath(storyName: String): String = s"stories/$storyName"

  def runAndWatch(): Unit = {
    val runnableStoriesPath = s"runners/$runnerName/stories"

    val jobWatcher =
      zkManager.watchChildren(runnableStoriesPath, fetchData = false) { (re, rw) =>
        re.getType match {
          // new story has been assigned to this runner
          case CHILD_ADDED =>
            val storyName    = Util.getLastPartOfPath(re.getData.getPath)
            val storyWatcher = runAndWatchStory(storyName)
            watchingStores += (storyName -> storyWatcher)

          // story has been removed from this runner
          case CHILD_REMOVED =>
            val storyName = Util.getLastPartOfPath(re.getData.getPath)
            logger.debug(s"removing story watcher of $storyName")
            storyRunner ! StoryRunner.Shutdown(Some(storyName))
            watchingStores
              .get(storyName)
              .foreach(w => {
                w.close()
                watchingStores.remove(storyName)
              })

          case _ =>
        }
      }

    appContext.addShutdownHook {
      watchingStores.foreach(_._2.close())
      jobWatcher.close()
    }
  }

  val storyInitStatus = mutable.Map.empty[String, AtomicBoolean]
  def runAndWatchStory(storyName: String): PathChildrenCache = {
    storyInitStatus += (storyName -> new AtomicBoolean())
    zkManager.watchChildren(getStoryRootPath(storyName), StartMode.POST_INITIALIZED_EVENT) { (se, sw) =>
      if (se.getType == INITIALIZED ||
          se.getType == CHILD_UPDATED ||
          (se.getType == CHILD_ADDED && storyInitStatus(storyName).get()) ||
          se.getType == CHILD_REMOVED) {

        if (se.getType == INITIALIZED) {
          storyInitStatus(storyName).compareAndSet(false, true)
        }

        val storyOption = createStory(storyName, sw.getCurrentData.asScala.toList)
        storyOption.foreach(story => {
          if (se.getType == INITIALIZED)
            storyRunner ! StoryRunner.Run(story)
          else
            storyRunner ! StoryRunner.Rerun(story)
        })
      }
    }
  }

  def createStory(storyName: String, data: List[ChildData]): Option[Story] =
    try {
      val storyInfo =
        createStoryInfoFromZKData(storyName, data)
      logger.debug(s"new story info: $storyInfo")
      val story = storyBuilder.buildStory(storyInfo)
      Some(story)
    } catch {
      case NonFatal(ex) =>
        logger.error(s"fetching or building story failed with error $ex, when run story")
        None
    }

  def createStoryInfoFromZKData(storyName: String, data: List[ChildData]): StoryInfo = {
    val storyData = mutable.Map.empty[String, String]
    data.foreach(child => {
      storyData += (Util.getLastPartOfPath(child.getPath) -> new String(child.getData, "UTF-8"))
    })

    StoryInfo(
      storyName,
      storyData.getOrElse("status", "INIT"),
      storyData.getOrElse("settings", ""),
      storyData("source"),
      storyData("sink"),
      storyData.get("transforms"),
      storyData.get("fallback")
    )
  }
}
