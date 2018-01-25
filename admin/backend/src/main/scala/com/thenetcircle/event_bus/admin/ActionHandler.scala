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

class ActionHandler(zkManager: ZooKeeperManager)(implicit appContext: AppContext, system: ActorSystem) {

  def getZKNodeTreeAsConfigString(path: String, depth: Int = 1): String = {
    val subNodes = zkManager.getChildren(path)
    var block    = ""
    val prevPad  = "".padTo((depth - 1) * 2, ' ')
    val pad      = "".padTo(depth * 2, ' ')

    if (subNodes.isDefined && subNodes.get.nonEmpty) {
      block += "{\n"
      subNodes.foreach(_.foreach(nodename => {
        block += pad + s"$nodename = " + getZKNodeTreeAsConfigString(s"$path/$nodename", depth + 1) + "\n"
      }))
      if (depth == 1)
        block += "}\n"
      else
        block += prevPad + "}"
    } else {
      if (depth == 1) {
        block = s"$path = " + zkManager.getData(path).getOrElse("") + "\n"
      } else {
        block = zkManager.getData(path).getOrElse("").replaceAll("\n|\r", "")
      }
    }

    block
  }

}
