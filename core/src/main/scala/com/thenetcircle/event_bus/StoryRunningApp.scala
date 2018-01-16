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
import com.thenetcircle.event_bus.misc.{BaseEnvironment, ZKManager}
import com.thenetcircle.event_bus.story._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.duration._

object StoryRunningApp extends App with StrictLogging {

  logger.info("Application is initializing.")

  // Initialize BaseEnvironment
  val env: BaseEnvironment = BaseEnvironment(ConfigFactory.load())

  // Check Executor Name
  var runnerGroup: String = if (args.length > 0) args(0) else ""
  if (runnerGroup.isEmpty)
    runnerGroup = env.getSystemConfig().getString("app.default-runner-group")

  // Connecting Zookeeper
  val zkManager: ZKManager = ZKManager(env.getSystemConfig())(env)
  zkManager.init()
  val runnerId: String = zkManager.registerStoryRunner(runnerGroup)

  // Create ActorSystem
  implicit val system: ActorSystem =
    ActorSystem(env.getAppName(), env.getSystemConfig())

  implicit val runningEnv: RunningEnvironment =
    RunningEnvironment(runnerGroup, runnerId)(env, system)

  // Kamon.start()

  // Run Stories
  val storyManager = StoryManager(zkManager, TaskBuilderFactory(runningEnv.getSystemConfig()))
  storyManager.run()

  sys.addShutdownHook({
    logger.info("Application is shutting down...")
    // Kamon.shutdown()
    runningEnv.shutdown()
    system.terminate()
    Await.result(system.whenTerminated, 60.seconds)
  })

}
