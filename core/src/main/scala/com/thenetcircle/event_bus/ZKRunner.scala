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

import akka.actor.{ActorRef, ActorSystem, Cancellable}
import akka.pattern.gracefulStop
import com.thenetcircle.event_bus.misc.{Logging, Util, ZKManager}
import com.thenetcircle.event_bus.story.StoryBuilder.StoryInfo
import com.thenetcircle.event_bus.story.{StoryBuilder, _}
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type._
import org.apache.curator.framework.recipes.cache.{PathChildrenCache, PathChildrenCacheEvent}
import org.apache.curator.framework.recipes.leader.LeaderLatch

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal

class ZKRunner private (runnerName: String, zkManager: ZKManager, storyBuilder: StoryBuilder)(
    implicit appContext: AppContext,
    system: ActorSystem
) extends Logging {

  // init StoryRunner
  val storyRunner: ActorRef =
    system.actorOf(StoryRunner.props(runnerName), "story-runner:" + runnerName)
  appContext.addShutdownHook {
    try {
      Await.ready(
        gracefulStop(storyRunner, 3.seconds, StoryRunner.Commands.Shutdown()),
        3.seconds
      )
    } catch {
      case ex: Throwable =>
    }
  }

  type ZKEvent   = PathChildrenCacheEvent
  type ZKWatcher = PathChildrenCache

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
      try {
        latch.close()
      } catch {
        case _: Throwable =>
      }
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
    var storyScheduler: Option[Cancellable] = None
    val jobWatcher =
      zkManager.watchChildren(assignedStoriesPath) { (_event, _watcher) =>
        _event.getType match {

          // new story has been assigned to this runner
          case CHILD_ADDED =>
            val storyName = Util.getLastPartOfPath(_event.getData.getPath)
            val amount    = getStoryRunningAmount(_event.getData.getData)
            logger.info(s"new story $storyName is assigned to runner $runnerName to run $amount times")

            // Run the story
            runStory(storyName, amount)

          case CHILD_UPDATED =>
            val storyName = Util.getLastPartOfPath(_event.getData.getPath)
            val amount    = getStoryRunningAmount(_event.getData.getData)
            logger.info(s"story $storyName is updated with amount $amount")

            shutdownStory(storyName)

            storyScheduler.foreach(_.cancel())
            storyScheduler = Option(
              system.scheduler.scheduleOnce(3.seconds) {
                runStory(storyName, amount)
              }(system.dispatcher)
            )

          // story has been removed from this runner
          case CHILD_REMOVED =>
            val storyName = Util.getLastPartOfPath(_event.getData.getPath)
            logger.info(s"story $storyName is removed from runner $runnerName")
            shutdownStory(storyName)

          case _ =>
        }
      }

    appContext.addShutdownHook {
      jobWatcher.close()
    }
  }

  def getStoryRunningAmount(data: Array[Byte]): Int = {
    var amount =
      try { Util.makeUTF8String(data).toInt } catch { case _: Throwable => 1 }
    if (amount <= 1 || amount >= 100) {
      amount = 1
    }
    amount
  }

  def runStory(storyName: String, amount: Int): Unit = {
    logger.info(s"going to run story $storyName $amount times")
    zkManager
      .getChildrenData(getStoryRootPath(storyName))
      .foreach(data => {
        for (i <- 1 to amount) {
          createStory(storyName, data).foreach(story => {
            storyRunner ! StoryRunner.Commands.Run(story)
          })
        }
      })
  }

  def shutdownStory(storyName: String): Unit =
    storyRunner ! StoryRunner.Commands.Shutdown(Some(storyName))

  def createStory(storyName: String, storyData: Map[String, String]): Option[Story] =
    try {
      val storyInfo = createStoryRawData(storyName, storyData)
      logger.info(s"creating new story $storyName with data: $storyInfo")
      val story = storyBuilder.buildStory(storyInfo)
      Some(story)
    } catch {
      case NonFatal(ex) =>
        logger.error(s"creating story $storyName failed with error $ex")
        None
    }

  def createStoryRawData(storyName: String, storyData: Map[String, String]): StoryInfo =
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

object ZKRunner {
  private var _instances: Map[String, ZKRunner] = Map.empty

  def apply(runnerName: String, zkManager: ZKManager, storyBuilder: StoryBuilder)(
      implicit appContext: AppContext,
      system: ActorSystem
  ): ZKRunner =
    _instances.getOrElse(
      runnerName, {
        val _runner = new ZKRunner(runnerName, zkManager, storyBuilder)
        _instances = _instances.updated(runnerName, _runner)
        _runner
      }
    )

}
