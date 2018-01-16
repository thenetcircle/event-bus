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

class KafkaSinkBuilderTest extends AkkaStreamTest {

  behavior of "KafkaSinkBuilder"

  val builder = new KafkaSinkBuilder

  it should "build correct KafkaSink with the default config" in {

    val sink = builder.build("""{
        |  "bootstrap-servers": "abc",
        |  "close-timeout": "100s",
        |  "parallelism": 50
        |}""".stripMargin)

    val settings = sink.settings

    settings.bootstrapServers shouldEqual "abc"
    settings.closeTimeout shouldEqual 100.seconds
    settings.parallelism shouldEqual 50

  }

}
