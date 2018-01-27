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
import akka.pattern.gracefulStop
import com.thenetcircle.event_bus.misc.{ZKStoryManager, ZooKeeperManager}
import com.thenetcircle.event_bus.story.StoryRunner
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Await
import scala.concurrent.duration._

class Main extends Core {

  val config: Config = ConfigFactory.load()

  // Initialize StoryRunner
  val runnerName: String = config.getString("app.runner-name")
  val storyRunner: ActorRef =
    system.actorOf(StoryRunner.props(runnerName), "runner-" + runnerName)
  appContext.addShutdownHook {
    Await.result(
      gracefulStop(storyRunner, 3.seconds, StoryRunner.Shutdown()),
      3.seconds
    )
  }

  // Setup Zookeeper
  val rootPath: String = config.getString("app.zkroot") + s"/${appContext.getAppName()}/${appContext.getAppEnv()}"
  val zkManager: ZooKeeperManager =
    ZooKeeperManager.createInstance(config.getString("app.zkserver"), rootPath)

  appContext.setZKManager(zkManager)

  def run(args: Array[String]): Unit =
    new ZKStoryManager(zkManager, runnerName, storyRunner).runAndWatch()

}

object Main extends App { (new Main).run(args) }
