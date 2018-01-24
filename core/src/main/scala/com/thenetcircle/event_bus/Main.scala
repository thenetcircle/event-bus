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

import akka.actor.ActorRef
import com.thenetcircle.event_bus.misc.{ZKManager, ZKStoryManager}
import com.thenetcircle.event_bus.story.StoryRunner
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import akka.pattern.gracefulStop

import scala.concurrent.Await
import scala.concurrent.duration._

object Main extends Core with App with StrictLogging {

  val config: Config = ConfigFactory.load()

  // Initialize StoryRunner
  val runnerName = config.getString("app.runner-name")
  val storyRunner: ActorRef =
    system.actorOf(StoryRunner.props(runnerName), "runner-" + runnerName)
  appContext.addShutdownHook {
    Await.result(
      gracefulStop(storyRunner, 3.seconds, StoryRunner.Shutdown()),
      3.seconds
    )
  }

  // Setup Zookeeper
  val zkManager: ZKManager = ZKManager(config.getString("app.zkserver"), config.getString("app.zkroot"))
  zkManager.start()
  appContext.setZKManager(zkManager)

  // Start run
  new ZKStoryManager(zkManager, runnerName, storyRunner).runAndWatch()

}
