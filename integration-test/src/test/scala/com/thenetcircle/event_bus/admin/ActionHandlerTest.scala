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
import com.typesafe.config.ConfigFactory

class ActionHandlerTest extends IntegrationTestBase {

  behavior of "ActionHandler"

  it should "get correct tree structure data from zookeeper" in {
    val zkManager =
      ZooKeeperManager("maggie-zoo-1:2181,maggie-zoo-2:2181", s"/event-bus/popp-lab")
    zkManager.start()
    val actionHandler = new ActionHandler(zkManager)

    val a = actionHandler.getZKNodeTreeAsJson("dev")

    logger.debug(a)
  }

  it should "handler config string" in {
    val str =
      """
        |{
        |  "stories": {
        |    "http-to-kafka": {
        |      "transforms": "tnc-topic-resolver#{}",
        |      "source": "http#{  \"port\": 8899,  \"succeeded-response\": \"ok\"}",
        |      "sink": "kafka#{  \"bootstrap-servers\": \"maggie-kafka-1:9093,maggie-kafka-2:9093,maggie-kafka-3:9093\"}",
        |      "status": "INIT"
        |    },
        |    "dino-forwarder": {
        |      "transforms": "tnc-dino-forwarder#{}|||tnc-topic-resolver#{}",
        |      "source": "kafka#{  \"bootstrap-servers\": \"maggie-kafka-1:9093,maggie-kafka-2:9093,maggie-kafka-3:9093\",  \"topics\": [ \"event-poppen-dino-wio\" ]}",
        |      "sink": "kafka#{ \"bootstrap-servers\": \"maggie-kafka-1:9093,maggie-kafka-2:9093,maggie-kafka-3:9093\" }"
        |    },
        |    "update-to-masterbenn": {
        |      "source": "kafka#{\"bootstrap-servers\": \"maggie-kafka-1:9093,maggie-kafka-2:9093,maggie-kafka-3:9093\", \"topics\": [ \"event-dino\"]}",
        |      "sink": "http#{ \"request\" : { \"uri\": \"http://master.benn.poppen.lab/frontend_dev.php/api/internal/tnc_event_dispatcher/receiver\" }, \"expected-response\": \"ok\" }"
        |    },
        |    "update-to-community": {
        |      "source": "kafka#{\"bootstrap-servers\": \"maggie-kafka-1:9093,maggie-kafka-2:9093,maggie-kafka-3:9093\",  \"topics\": [ \"event-dino\"]}",
        |      "sink": "http#{  \"request\" : {    \"uri\": \"http://beta.blue.guangqi.poppen.lab/frontend_dev.php/api/internal/tnc_event_dispatcher/receiver\"  },  \"expected-response\": \"ok\"}"
        |    }
        |  },
        |  "runners": {
        |    "default-runner": {
        |      "stories": {
        |        "dino-forwarder": "",
        |        "update-to-masterbenn": "",
        |        "update-to-community": ""
        |      }
        |    }
        |  },
        |  "topics": {
        |    "event-user": "user.*|||profile.*",
        |    "event-message": "message.*",
        |    "event-dino": "dino\\..*",
        |    "event-bustest": "bus-.*"
        |  }
        |}
      """.stripMargin

    println(ConfigFactory.parseString(str).toString)
  }

}
