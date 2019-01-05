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

import com.thenetcircle.event_bus.context.TaskBuildingContext
import com.thenetcircle.event_bus.misc.{Monitor, ZKManager}
import com.thenetcircle.event_bus.story.builder.{StoryBuilder, TaskBuilderFactory}
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
    val config        = appContext.getSystemConfig()
    val connectString = config.getString("app.zookeeper.servers")
    val rootPath      = config.getString("app.zookeeper.rootpath") + s"/${appContext.getAppName()}/${appContext.getAppEnv()}"
    val zkManager     = new ZKManager(connectString, rootPath)

    // set zkManager to appContext
    appContext.addShutdownHook(zkManager.close())
    appContext.setZKManager(zkManager)

    zkManager
  }

  private def initStoryBuilder(): StoryBuilder = {
    config.checkValid(ConfigFactory.defaultReference, "task.builders")

    // initialize TaskBuilderFactory
    val taskBuilderFactory = new TaskBuilderFactory()
    List("source", "transform", "sink", "fallback").foreach(prefix => {
      config
        .as[List[List[String]]](s"task.builders.$prefix")
        .foreach {
          case category :: builderClassName :: _ =>
            taskBuilderFactory.registerBuilder(category, builderClassName)
          case _ =>
        }
    })
    // initialize TaskBuildingContext
    val taskBuildingContext = new TaskBuildingContext(appContext)

    new StoryBuilder(taskBuilderFactory, taskBuildingContext)
  }
}

object Runner extends App { (new Runner).run(args) }
