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

import com.thenetcircle.event_bus.IntegrationTestBase
import com.thenetcircle.event_bus.misc.ZooKeeperManager

class ActionHandlerTest extends IntegrationTestBase {

  behavior of "ActionHandler"

  it should "get correct tree structure data from zookeeper" in {
    val zkManager =
      ZooKeeperManager("maggie-zoo-1:2181,maggie-zoo-2:2181", s"/event-bus/popp-lab")
    zkManager.start()
    val actionHandler = new ActionHandler(zkManager)

    val a = actionHandler.getZKNodeTreeAsConfigString("dev/stories/http-to-kafka")

    logger.debug(a)
  }

}
