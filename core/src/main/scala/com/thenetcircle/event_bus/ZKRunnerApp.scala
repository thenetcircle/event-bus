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

import akka.actor.{ActorRef, ActorSystem}
import com.thenetcircle.event_bus.context.AppContext
import com.thenetcircle.event_bus.helper.ZookeeperManager
import com.thenetcircle.event_bus.story._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.apache.curator.framework.recipes.cache.PathChildrenCache
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal

object ZKRunnerApp extends App with StrictLogging {

  logger.info("Application is initializing.")

  // Base components
  val config: Config = ConfigFactory.load()
  implicit val appContext: AppContext = AppContext(config)
  implicit val system: ActorSystem = ActorSystem(appContext.getAppName(), config)

  // Initialize StoryRunner
  var runnerName: String = config.getString("app.runner-name")
  val storyRunner: ActorRef =
    system.actorOf(StoryRunner.props(runnerName), "runner-" + runnerName)

  // Setup shutdown hooks
  sys.addShutdownHook({
    logger.info("Application is shutting down...")
    Await
      .result(akka.pattern.gracefulStop(storyRunner, 3.seconds, StoryRunner.Shutdown()), 3.seconds)
    appContext.shutdown()
    system.terminate()
    Await.result(system.whenTerminated, 6.seconds)
  })

  // Setup Zookeeper
  if (args.length == 0 || args(0).isEmpty) {
    throw new RuntimeException("Argument 1 zookeeper connect string is required.")
  }
  val zookeeperConnectString = args(0)
  val zkManager: ZookeeperManager =
    ZookeeperManager.init(zookeeperConnectString, s"/event-bus/${appContext.getAppName()}")
  zkManager.registerStoryRunner(runnerName)

  // Fetch stories and run
  val storyBuilder: StoryBuilder = StoryBuilder(TaskBuilderFactory(config))
  val storyDAO: StoryZookeeperDAO = StoryZookeeperDAO(zkManager)

  val printColor = (msg: String) ⇒ s"""\u001B[32m${msg}B[0m"""
  def runStory(storyName: String, restart: Boolean = false): Unit = {
    try {
      // TODO use the data returned by watcher
      val storyInfo = storyDAO.getStoryInfo(storyName)
      val story = storyBuilder.buildStory(storyInfo)
      if (!restart)
        storyRunner ! StoryRunner.Run(story)
      else
        storyRunner ! StoryRunner.Restart(story)
    } catch {
      case NonFatal(ex) =>
        logger
          .error(
            printColor(
              s"fetching or building story failed with error $ex, the story will not be run"
            )
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

  import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type._
  var watchedStores = mutable.Map.empty[String, (PathChildrenCache, PathChildrenCache)]
  // watch assigned stories
  zkManager.watchChildren(s"runners/$runnerName/stories", StartMode.NORMAL) { event =>
    val path = event.getData.getPath
    event.getType match {
      case CHILD_ADDED =>
        val storyName = getLastPartOfPath(path)

        runStory(storyName)

        val storyWatcher =
          zkManager.watchChildren(s"stories/$storyName", StartMode.BUILD_INITIAL_CACHE) { event =>
            if (event.getType == CHILD_UPDATED) {
              val child = getLastPartOfPath(event.getData.getPath)
              if (child == "source" || child == "sink" || child == "fallback") {
                runStory(storyName, true)
              }
            }
          }

        val storyTransformsWatcher =
          zkManager.watchChildren(s"stories/$storyName/transforms", StartMode.BUILD_INITIAL_CACHE) {
            event =>
              if (event.getType == CHILD_UPDATED || event.getType == CHILD_ADDED || event.getType == CHILD_REMOVED) {
                runStory(storyName, true)
              }
          }

        watchedStores += (storyName -> (storyWatcher, storyTransformsWatcher))

      case CHILD_REMOVED =>
        val storyName = getLastPartOfPath(path)
        shutdownStory(storyName)

        watchedStores
          .get(storyName)
          .foreach(w => {
            w._1.close()
            w._2.close()
          })
    }
  }

}
