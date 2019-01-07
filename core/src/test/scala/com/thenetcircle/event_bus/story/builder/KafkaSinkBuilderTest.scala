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

package com.thenetcircle.event_bus.story.builder

import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.story.tasks.kafka.{KafkaSink, KafkaSinkBuilder}

import scala.concurrent.duration._

class KafkaSinkBuilderTest extends TestBase {

  behavior of "KafkaSinkBuilder"

  val builder = new KafkaSinkBuilder

  it should "build correct KafkaSink with the default config" in {

    val sink = storyBuilder.buildTask("""{
        |  "bootstrap-servers": "testserver1,testserver2",
        |  "close-timeout": "100 s",
        |  "parallelism": 50,
        |  "use-dispatcher": "test-dispatcher",
        |  "properties": {
        |    "batch.size": 1024,
        |    "connections.max.idle.ms": 100,
        |    "max.in.flight.requests.per.connection": 10
        |  }
        |}""".stripMargin)(builder)

    val settings         = sink.settings
    val kafkaSink        = new KafkaSink(settings)
    val producerSettings = kafkaSink.getProducerSettings()

    settings.bootstrapServers shouldEqual "testserver1,testserver2"
    settings.closeTimeout shouldEqual 100.seconds
    settings.parallelism shouldEqual 50
    settings.useDispatcher.get shouldEqual "test-dispatcher"

    settings.useAsyncBuffer shouldEqual true
    settings.asyncBufferSize shouldEqual 100
    producerSettings.properties("acks") shouldEqual "all"
    producerSettings.properties("retries") shouldEqual "30"
    producerSettings.properties("max.in.flight.requests.per.connection") shouldEqual "10"
    producerSettings.properties("enable.idempotence") shouldEqual "true"

    producerSettings.properties("batch.size") shouldEqual "1024"
    producerSettings.properties("connections.max.idle.ms") shouldEqual "100"

  }

}
