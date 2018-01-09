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

package com.thenetcircle.event_bus.plots.kafka

import com.thenetcircle.event_bus.base.AkkaStreamTest

import scala.concurrent.duration._

class KafkaSinkBuilderTest extends AkkaStreamTest {

  behavior of "KafkaSinkBuilder"

  val builder = new KafkaSinkBuilder

  it should "build correct KafkaSink with the default config" in {

    val sink = builder.build("""{
        |  "producer": {
        |    "close-timeout": "100s",
        |    "use-dispatcher": "test-dispatcher"
        |  }
        |}""".stripMargin)

    val settings = sink.settings

    settings.producerSettings.parallelism shouldEqual 100
    settings.producerSettings.closeTimeout shouldEqual 100.seconds
    settings.producerSettings.dispatcher shouldEqual "test-dispatcher"
    settings.producerSettings.properties("client.id") shouldEqual "EventBus-Producer"

  }

}
