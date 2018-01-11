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

package com.thenetcircle.event_bus.tasks.kafka

import com.thenetcircle.event_bus.base.AkkaStreamTest

import scala.concurrent.duration._

class KafkaTaskABuilderTest extends AkkaStreamTest {

  behavior of "KafkaTaskABuilder"

  val builder = new KafkaTaskABuilder

  it should "build correct KafkaTaskA with the default config" in {

    val sink = builder.build("""{
        |  "group-id": "test-group",
        |  "topics": [ "abc", "def" ],
        |  "commit-batch-max": 100,
        |  "consumer": {
        |    "poll-timeout": "50ms"
        |  }
        |}""".stripMargin)

    val settings = sink.settings

    settings.groupId shouldEqual "test-group"
    settings.topics shouldEqual Some(Set("abc", "def"))
    settings.commitBatchMax shouldEqual 100
    settings.consumerSettings.pollTimeout shouldEqual 50.milliseconds
    settings.consumerSettings.dispatcher shouldEqual "akka.kafka.default-dispatcher"
    settings.consumerSettings.properties("client.id") shouldEqual "EventBus-Consumer"

  }

}
