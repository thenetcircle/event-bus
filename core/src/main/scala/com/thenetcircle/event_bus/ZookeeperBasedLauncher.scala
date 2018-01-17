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
import com.thenetcircle.event_bus.context.AppContext
import com.thenetcircle.event_bus.helper.ZKManager
import com.thenetcircle.event_bus.story._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.duration._

object ZookeeperBasedLauncher extends App with StrictLogging {

  logger.info("Application is initializing.")

  val config: Config = ConfigFactory.load()

  // Check Executor Name
  // TODO append server info
  var runnerName: String = if (args.length > 0) args(0) else ""
  if (runnerName.isEmpty)
    runnerName = config.getString("app.default-runner-group")

  // Create AppContext
  implicit val appContext: AppContext = AppContext(config)

  // Connecting Zookeeper
  val zkManager: ZKManager = ZKManager(config)(appContext)
  zkManager.init()
  zkManager.registerStoryRunner(runnerName)

  // Create ActorSystem
  implicit val system: ActorSystem = ActorSystem(appContext.getAppName(), config)

  // Kamon.start()

  // Run Stories
  val storyManager = StoryRunner(zkManager, TaskBuilderFactory(config))
  storyManager.run()

  sys.addShutdownHook({
    logger.info("Application is shutting down...")
    // Kamon.shutdown()
    appContext.shutdown()
    system.terminate()
    Await.result(system.whenTerminated, 60.seconds)
  })

}
