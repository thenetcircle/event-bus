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

class ActionHandlerTest extends IntegrationTestBase {

  behavior of "ActionHandler"

  val actionHandler = new ActionHandler(zkManager)

  it should "handler config string" in {
    val str =
      """
        |{
        |  "transforms": "tnc-topic-resolver#{}",
        |  "source": "http#{  \"port\": 8899,  \"succeeded-response\": \"ok\"}",
        |  "sink": {
        |    "item1": "kafka#{  \"bootstrap-servers\": \"maggie-kafka-1:9093,maggie-kafka-2:9093,maggie-kafka-3:9093\"}"
        |  },
        |  "status": "INIT"
        |}
      """.stripMargin

    actionHandler.updateZKNodeTreeByJson("test", str)

    actionHandler
      .getZKNodeTreeAsJson("test/transforms")
      .trim shouldEqual """tnc-topic-resolver#{}"""

    actionHandler
      .getZKNodeTreeAsJson("test/sink/item1")
      .trim shouldEqual """kafka#{  "bootstrap-servers": "maggie-kafka-1:9093,maggie-kafka-2:9093,maggie-kafka-3:9093"}"""

    // actionHandler.getZKNodeTreeAsJson("test", false) shouldEqual """{"transforms": "tnc-topic-resolver#{}","source": "http#{  \"port\": 8899,  \"succeeded-response\": \"ok\"}","sink": {"item1": "kafka#{  \"bootstrap-servers\": \"maggie-kafka-1:9093,maggie-kafka-2:9093,maggie-kafka-3:9093\"}"},"status": "INIT"}"""

    zkManager.deletePath("test")

    actionHandler.getZKNodeTreeAsJson("test") shouldEqual ""
  }

}
