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
import com.thenetcircle.event_bus.misc.{Util, ZKManager}
import com.thenetcircle.event_bus.story._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode
import org.apache.curator.framework.recipes.cache.{PathChildrenCache, PathChildrenCacheEvent}

import scala.collection.mutable
import scala.concurrent.Await
import scala.util.control.NonFatal
import scala.concurrent.duration._

object ZKBasedRunner extends App with StrictLogging {
  logger.info("Application is initializing.")

  // Base components
  val config: Config = ConfigFactory.load()
  implicit val appContext: AppContext = AppContext(config)
  implicit val system: ActorSystem = ActorSystem(appContext.getAppName(), config)

  // Initialize StoryRunner
  checkArg(0, "the first argument runner-name is required")
  var runnerName: String = args(0)
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
  checkArg(1, "the second argument zkserver is required")
  val zookeeperConnectString = args(1)
  val zkRootPath = s"/event-bus/${appContext.getAppName()}/${appContext.getAppEnv()}"
  val zkManager: ZKManager = ZKManager.init(zookeeperConnectString, zkRootPath)

  // Setup builder
  val storyBuilder: StoryBuilder = StoryBuilder(TaskBuilderFactory(config))

  // Start run
  watchAndRunStories()

  def checkArg(index: Int, message: String): Unit = {
    if (args.length <= index) {
      Console.err.println(message)
      sys.exit(1)
    }
  }

  // Fetch stories and run
  def watchAndRunStories(): Unit = {
    import PathChildrenCacheEvent.Type._
    type Watcher = PathChildrenCache

    // register runner
    zkManager.registerStoryRunner(runnerName)

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
