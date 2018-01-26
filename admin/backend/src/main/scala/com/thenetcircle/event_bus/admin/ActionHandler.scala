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

package com.thenetcircle.event_bus.admin

import akka.actor.ActorSystem
import com.thenetcircle.event_bus.context.AppContext
import com.thenetcircle.event_bus.misc.ZooKeeperManager
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

class ActionHandler(zkManager: ZooKeeperManager)(implicit appContext: AppContext, system: ActorSystem) {

  def getZKNodeTreeAsJson(path: String, depth: Int = 1): String = {
    val subNodes = zkManager.getChildren(path)
    var block    = ""
    val prevPad  = "".padTo((depth - 1) * 2, ' ')
    val pad      = "".padTo(depth * 2, ' ')

    if (subNodes.isDefined && subNodes.get.nonEmpty) {
      block += "{\n"
      subNodes.foreach(nodeList => {
        for (i <- nodeList.indices) {
          val nodename = nodeList(i)
          block += pad + s""""$nodename": """ +
            getZKNodeTreeAsJson(s"$path/$nodename", depth + 1)
          if (i < nodeList.length - 1)
            block += ","
          block += "\n"
        }
      })
      if (depth == 1)
        block += "}\n"
      else
        block += prevPad + "}"
    } else {
      if (depth == 1) {
        block = s""""$path": """ + "\"" + zkManager
          .getData(path)
          .getOrElse("")
          .replaceAll("""\\""", """\\\\""")
          .replaceAll("\"", "\\\\\"") + "\"" + "\n"
      } else {
        block = "\"" + zkManager
          .getData(path)
          .getOrElse("")
          .replaceAll("\n|\r", "")
          .replaceAll("""\\""", """\\\\""")
          .replaceAll("\"", "\\\\\"") + "\""
      }
    }

    block
  }

  def updateZKNodeTreeByJson(path: String, json: String): String = {
    zkManager.ensurePath(path)
    val
    val config = ConfigFactory.parseString(s"""
        |"$path" $json
      """.stripMargin).getObjectList()

    config.as[Map[String, Config]](path).toString()
  }

}
