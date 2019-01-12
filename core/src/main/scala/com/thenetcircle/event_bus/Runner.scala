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

import com.thenetcircle.event_bus.misc.{Monitor, ZKManager}
import com.thenetcircle.event_bus.story.StoryBuilder
import com.thenetcircle.event_bus.story.interfaces.{IOperator, ISink, ISource}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

class Runner extends AbstractApp {

  val config: Config = ConfigFactory.load()

  def run(args: Array[String]): Unit = {
    Monitor.init()
    ZKRunner(config.getString("app.runner-name"), initZKManager(), initStoryBuilder()).waitAndStart()
  }

  private def initZKManager(): ZKManager = {
    // initialize variables
    val connectString = config.getString("app.zookeeper.servers")
    val rootPath      = config.getString("app.zookeeper.rootpath") + s"/${appContext.getAppName()}/${appContext.getAppEnv()}"
    val zkManager     = new ZKManager(connectString, rootPath)

    // set zkManager to appContext
    appContext.addShutdownHook(zkManager.close())
    appContext.setZKManager(zkManager)

    zkManager
  }

  private def initStoryBuilder(): StoryBuilder = {
    config.checkValid(ConfigFactory.defaultReference, "app.task.builders")

    val storyBuilder = new StoryBuilder()

    val buildersConfig = config.getConfig("app.task.builders")
    buildersConfig.as[Option[List[String]]]("source").foreach(_.foreach(storyBuilder.addTaskBuilder[ISource]))
    buildersConfig
      .as[Option[List[String]]]("operators")
      .foreach(_.foreach(storyBuilder.addTaskBuilder[IOperator]))
    buildersConfig.as[Option[List[String]]]("sink").foreach(_.foreach(storyBuilder.addTaskBuilder[ISink]))

    storyBuilder
  }
}

object Runner extends App { (new Runner).run(args) }
