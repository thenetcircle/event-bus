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

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorRef, ActorSystem}
import com.thenetcircle.event_bus.BuildInfo
import com.thenetcircle.event_bus.context.AppContext
import com.thenetcircle.event_bus.misc.{Util, ZooKeeperManager}
import com.typesafe.scalalogging.StrictLogging
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type._
import org.apache.curator.framework.recipes.cache.{ChildData, PathChildrenCache, PathChildrenCacheEvent}
import org.apache.curator.framework.recipes.leader.LeaderLatch

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal

object StoryZooKeeperListener {
  def apply(runnerName: String, storyRunner: ActorRef, storyBuilder: StoryBuilder)(
      implicit appContext: AppContext,
      system: ActorSystem
  ): StoryZooKeeperListener =
    new StoryZooKeeperListener(runnerName, storyRunner, storyBuilder)
}

class StoryZooKeeperListener(runnerName: String, storyRunner: ActorRef, storyBuilder: StoryBuilder)(
    implicit appContext: AppContext,
    system: ActorSystem
) extends StrictLogging {

  require(
    appContext.getZooKeeperManager().isDefined,
    "StoryZooKeeperListener requires AppContext with ZookeeperManager injected"
  )

  val zkManager: ZooKeeperManager = appContext.getZooKeeperManager().get

  type ZKEvent   = PathChildrenCacheEvent
  type ZKWatcher = PathChildrenCache

  private val watchingStores = mutable.Map.empty[String, ZKWatcher]

  val runnerPath = s"runners/$runnerName"

  zkManager.ensurePath(runnerPath)
  zkManager.ensurePath("stories")

  def getStoryRootPath(storyName: String): String = s"stories/$storyName"

  val assignedStoriesPath = s"$runnerPath/stories"

  def waitAndStart(): Unit = {
    val latchPath = s"$runnerPath/latch"
    val latch     = new LeaderLatch(zkManager.getClient(), zkManager.getAbsPath(latchPath))
    latch.start()
    appContext.addShutdownHook {
      try { latch.close() } catch { case _: Throwable => }
    }

    try {
      logger.info(s"Runner $runnerName is going to get the leadership.")
      var isWaiting    = true
      val loggerRandom = Random
      while (isWaiting) {
        if (latch.hasLeadership) {
          logger.info(s"Runner $runnerName has got the leadership.")
          updateRunnerInfo()
          start()
          isWaiting = false
        } else {
          if (loggerRandom.nextInt(100) > 95) {
            logger.info(s"Runner $runnerName is still waiting for leadership.")
          }
          Thread.sleep(2000)
        }
      }
    } catch {
      case ex: Throwable =>
        logger.warn(s"Runner $runnerName is not waiting for leadership anymore because of the exception $ex.")
    }
  }

  def updateRunnerInfo(): Unit = {
    zkManager.ensurePath(s"$runnerPath/info")

    try {
      val runnerHost = try {
        java.net.InetAddress.getLocalHost.getHostName
      } catch {
        case _: Throwable => "unknown"
      }
      val runnerInfo =
        s"""
           |{
           |  "host": "$runnerHost",
           |  "version": "${BuildInfo.version}"
           |}
         """.stripMargin
      zkManager.setData(s"$runnerPath/info", runnerInfo)
    } catch {
      case _: Throwable =>
    }
  }

  def start(): Unit = {
    val jobWatcher =
      zkManager.watchChildren(assignedStoriesPath, fetchData = false) { (_event, _watcher) =>
        _event.getType match {

          // new story has been assigned to this runner
          case CHILD_ADDED =>
            val storyName = Util.getLastPartOfPath(_event.getData.getPath)
            logger.info(s"new story $storyName is assigned to runner $runnerName")
            val storyWatcher = watchStory(storyName)
            watchingStores += (storyName -> storyWatcher)

          // story has been removed from this runner
          case CHILD_REMOVED =>
            val storyName = Util.getLastPartOfPath(_event.getData.getPath)
            logger.info(s"story $storyName is removed from runner $runnerName")
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

  def watchStory(storyName: String): ZKWatcher = {
    val inited: AtomicBoolean = new AtomicBoolean(false)
    zkManager.watchChildren(getStoryRootPath(storyName), StartMode.POST_INITIALIZED_EVENT) { (_event, _watcher) =>
      if (_event.getType == INITIALIZED) {

        inited.compareAndSet(false, true)
        val optionStory = createStory(storyName, _event.getInitialData.asScala.toList)
        optionStory.foreach(s => storyRunner ! StoryRunner.Run(s))

      } else if (inited.get() == true &&
                 (_event.getType == CHILD_UPDATED ||
                 _event.getType == CHILD_ADDED ||
                 _event.getType == CHILD_REMOVED)) {

        val storyOption = createStory(storyName, _watcher.getCurrentData.asScala.toList)
        storyOption.foreach(s => storyRunner ! StoryRunner.ScheduleRerun(3.seconds, s))

      }
    }
  }

  def createStory(storyName: String, data: List[ChildData]): Option[Story] =
    try {
      val storyInfo = createStoryInfo(storyName, data)
      logger.info(s"story $storyName was inited or updated according to ZooKeeper data, info: $storyInfo")
      val story = storyBuilder.buildStory(storyInfo)
      Some(story)
    } catch {
      case NonFatal(ex) =>
        logger.error(s"fetching or building story $storyName failed according to the changes on ZooKeeper, error $ex")
        None
    }

  def createStoryInfo(storyName: String, data: List[ChildData]): StoryInfo = {
    val storyData = mutable.Map.empty[String, String]
    data.foreach(child => {
      storyData += (Util.getLastPartOfPath(child.getPath) -> Util.makeUTF8String(child.getData))
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
