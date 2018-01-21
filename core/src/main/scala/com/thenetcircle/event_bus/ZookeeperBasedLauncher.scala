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

import akka.actor.ActorSystem
import com.thenetcircle.event_bus.context.{AppContext, TaskRunningContextFactory}
import com.thenetcircle.event_bus.helper.ZookeeperManager
import com.thenetcircle.event_bus.story._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.duration._

object ZookeeperBasedLauncher extends App with StrictLogging {

  logger.info("Application is initializing.")

  // Base components
  val config: Config = ConfigFactory.load()
  implicit val appContext: AppContext = AppContext(config)
  implicit val system: ActorSystem = ActorSystem(appContext.getAppName(), config)

  // Setup Zookeeper
  val zkManager: ZookeeperManager =
    ZookeeperManager(
      config.getString("app.zookeeper-server"),
      s"/event-bus/${appContext.getAppName()}"
    )
  zkManager.start()
  appContext.setZookeeperManager(zkManager)

  // Run Stories
  // TODO append server info
  val storyRunner: StoryRunner =
    StoryRunner(if (args.length > 0) args(0) else "", TaskRunningContextFactory(system, appContext))
  zkManager.registerStoryRunner(storyRunner.getName())

  val storyBuilder: StoryBuilder = StoryBuilder(TaskBuilderFactory(config))
  val storyDAO: StoryDAO = StoryZookeeperDAO(zkManager)

  storyDAO
    .getStoriesByRunnerName(storyRunner.getName())
    .foreach(storyInfo => {
      val story = storyBuilder.buildStory(storyInfo)
      storyRunner.run(story)
    })

  sys.addShutdownHook({
    logger.info("Application is shutting down...")
    storyRunner.shutdown()
    appContext.shutdown()
    system.terminate()
    Await.result(system.whenTerminated, 60.seconds)
  })

}
