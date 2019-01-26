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
import com.thenetcircle.event_bus.story.tasks.kafka.{KafkaSource, KafkaSourceBuilder}

import scala.concurrent.duration._

class KafkaSourceBuilderTest extends TestBase {

  behavior of "KafkaSourceBuilder"

  val builder = new KafkaSourceBuilder

  it should "build correct KafkaSource with minimum config" in {

    val task = storyBuilder.buildTaskWithBuilder("""{
                                                   |  "bootstrap-servers": "test-server",
                                                   |  "topics": [ "abc", "def" ]
                                                   |}""".stripMargin)(builder)

    val settings         = task.settings
    val kafkaSource      = new KafkaSource(settings)
    val consumerSettings = kafkaSource.getConsumerSettings()

    settings.subscribedTopics shouldEqual Left(Set[String]("abc", "def"))
    settings.maxConcurrentPartitions shouldEqual 1024
    settings.commitMaxBatches shouldEqual 50

    consumerSettings.dispatcher shouldEqual "akka.kafka.default-dispatcher"
    consumerSettings.pollInterval shouldEqual 50.milliseconds
    consumerSettings.pollTimeout shouldEqual 50.milliseconds
    consumerSettings.stopTimeout shouldEqual 30.seconds
    consumerSettings.maxWakeups shouldEqual 10
    consumerSettings.waitClosePartition shouldEqual 500.milliseconds

    consumerSettings.getProperty("bootstrap.servers") shouldEqual "test-server"
    consumerSettings.getProperty("group.id") shouldEqual "event-bus_consumer_event-bus-test_test_unknown"
    consumerSettings.getProperty("enable.auto.commit") shouldEqual "false"
  }

  it should "build correct KafkaSource with the default config" in {

    val task = storyBuilder.buildTaskWithBuilder("""{
        |  "bootstrap-servers": "test-server",
        |  "group-id": "test-group",
        |  "topics": [ "abc", "def" ],
        |  "max-concurrent-partitions": 50,
        |  "commit-max-batches": 10,
        |  "akka-kafka": {
        |    "poll-interval": "10s",
        |    "poll-timeout": "500ms",
        |    "stop-timeout": "300s",
        |    "close-timeout": "12s",
        |    "commit-timeout": "1s",
        |    "commit-time-warning": "5s",
        |    "wakeup-timeout": "5s",
        |    "max-wakeups": "30",
        |    "use-dispatcher": "test-dispatcher",
        |    "wait-close-partition": "20ms",
        |    "wakeup-debug": false
        |  },
        |  "properties": {
        |    "enable.auto.commit": "true"
        |  }
        |}""".stripMargin)(builder)

    val settings         = task.settings
    val kafkaSource      = new KafkaSource(settings)
    val consumerSettings = kafkaSource.getConsumerSettings()

    settings.subscribedTopics shouldEqual Left(Set[String]("abc", "def"))
    settings.maxConcurrentPartitions shouldEqual 50
    settings.commitMaxBatches shouldEqual 10

    consumerSettings.pollInterval shouldEqual 10.seconds
    consumerSettings.pollTimeout shouldEqual 500.milliseconds
    consumerSettings.stopTimeout shouldEqual 300.seconds
    consumerSettings.closeTimeout shouldEqual 12.seconds
    consumerSettings.commitTimeout shouldEqual 1.second
    consumerSettings.commitTimeWarning shouldEqual 5.seconds
    consumerSettings.wakeupTimeout shouldEqual 5.seconds
    consumerSettings.maxWakeups shouldEqual 30
    consumerSettings.dispatcher shouldEqual "test-dispatcher"
    consumerSettings.waitClosePartition shouldEqual 20.milliseconds
    consumerSettings.wakeupDebug shouldEqual false

    consumerSettings.getProperty("bootstrap.servers") shouldEqual "test-server"
    consumerSettings.getProperty("group.id") shouldEqual "test-group"
    consumerSettings.getProperty("enable.auto.commit") shouldEqual "true"
  }

}
