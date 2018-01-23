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

import com.thenetcircle.event_bus.misc.{ZKManager, ZKStoryManager}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging

object ZKBasedRunner extends AbstractApp with StrictLogging {

  def main(args: Array[String]): Unit = {
    logger.info("Application is initializing.")

    val config: Config = ConfigFactory.load()

    if (args.length == 0) printUsageAndExit()
    val options = parseArgs(args.toList)

    val runnerName = options.getOrElse('runnername, config.getString("app.default-runner-name"))
    if (options.get('zkserver).isEmpty) printUsageAndExit()
    val zkConnectString = options('zkserver)

    implicit val (appContext, actorSystem, storyRunner) = initCoreComponents(config, runnerName)

    // Setup Zookeeper
    val zkRootPath = s"/event-bus/${appContext.getAppName()}/${appContext.getAppEnv()}"
    val zkManager: ZKManager = ZKManager.init(zkConnectString, zkRootPath)

    // Start run
    new ZKStoryManager(zkManager, runnerName, storyRunner).runAndWatch()
  }

  def printUsageAndExit(): Unit = {
    Console.err.println("""
          |Usage: bin/xxx [--runner-name xxx] [--zkserver xxx]
        """.stripMargin)
    sys.exit(1)
  }

  def parseArgs(list: List[String], map: Map[Symbol, String] = Map.empty): Map[Symbol, String] = {
    list match {
      case Nil => map
      case "--runner-name" :: value :: tail =>
        parseArgs(tail, map ++ Map('runnername -> value))
      case "--zkserver" :: value :: tail =>
        parseArgs(tail, map ++ Map('zkserver -> value))
      case string :: Nil => parseArgs(list.tail, map ++ Map('zkserver -> string))
      case arga :: argb :: Nil =>
        parseArgs(list.tail, map ++ Map('runnername -> arga, 'zkserver -> argb))
      case option :: tail =>
        println("Unknown option " + option)
        sys.exit(1)
    }
  }
}
