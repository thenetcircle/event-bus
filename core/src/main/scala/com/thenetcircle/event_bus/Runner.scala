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
import com.thenetcircle.event_bus.misc.{Monitor, ZKManager}
import com.thenetcircle.event_bus.story.{StoryBuilder, StoryRunner, TaskBuilderFactory}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Await
import scala.concurrent.duration._

class Runner extends AbstractApp {

  val config: Config = ConfigFactory.load()

  def run(args: Array[String]): Unit = {

    Monitor.init()
    ZKManager.init()

    // Initialize StoryRunner
    val runnerName: String = config.getString("app.runner-name")
    val storyRunner: ActorRef =
      system.actorOf(StoryRunner.props(runnerName), "runner:" + runnerName)
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

    val storyBuilder: StoryBuilder = StoryBuilder(TaskBuilderFactory(appContext.getSystemConfig()))

    StoryZKListener(runnerName, storyRunner, storyBuilder).waitAndStart()

  }
}

object Runner extends App { (new Runner).run(args) }
