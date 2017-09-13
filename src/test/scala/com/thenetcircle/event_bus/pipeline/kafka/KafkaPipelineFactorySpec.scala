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

package com.thenetcircle.event_bus.pipeline.kafka

import com.thenetcircle.event_bus.EventFormat
import com.thenetcircle.event_bus.testkit.AkkaBaseSpec
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.FiniteDuration

class KafkaPipelineFactorySpec extends AkkaBaseSpec {

  behavior of "KafkaPipelineFactory"

  it should "be singleton" in {
    KafkaPipelineFactory() shouldEqual KafkaPipelineFactory()
    new KafkaPipelineFactory() should not equal new KafkaPipelineFactory()
  }

  it should "properly create PipelineSettings" in {
    val config =
      ConfigFactory.parseString(
        """
                                             |{
                                             |  name = TestPipeline
                                             |  akka.kafka.producer {
                                             |    close-timeout = 999s
                                             |    use-dispatcher = "TestPipelineProducerDispatcher"
                                             |  }
                                             |  akka.kafka.consumer {
                                             |    poll-timeout = 999ms
                                             |    max-wakeups = 999
                                             |    use-dispatcher = "TestPipelineConsumerDispatcher"
                                             |  }
                                             |}
                                           """.stripMargin)

    val settings = KafkaPipelineFactory().createPipelineSettings(config)

    settings.name shouldEqual "TestPipeline"

    settings.producerSettings.parallelism shouldEqual 100
    settings.producerSettings.closeTimeout shouldEqual FiniteDuration(999, "s")
    settings.producerSettings.dispatcher shouldEqual "TestPipelineProducerDispatcher"

    settings.consumerSettings.stopTimeout shouldEqual FiniteDuration(30, "s")
    settings.consumerSettings.pollTimeout shouldEqual FiniteDuration(999, "ms")
    settings.consumerSettings.maxWakeups shouldEqual 999
    settings.consumerSettings.dispatcher shouldEqual "TestPipelineConsumerDispatcher"
  }

  it should "properly create PipelineInletSettings" in {
    val config =
      ConfigFactory.parseString("""
          |{
          |  close-timeout = 999ms
          |  parallelism   = 10
          |}
        """.stripMargin)

    val settings = KafkaPipelineFactory().createPipelineInletSettings(config)

    settings.closeTimeout shouldEqual Some(FiniteDuration(999, "ms"))
    settings.parallelism shouldEqual Some(10)
  }

  it should "properly create PipelineOutletSettings" in {
    val config =
      ConfigFactory.parseString("""
                                  |{
                                  |  group-id = "TestGroup"
                                  |  extract-parallelism = 999
                                  |  event-format = test
                                  |  topics = ["a", "b"]
                                  |  topicPattern = "event-*"
                                  |  stop-timeout = 999s
                                  |  wakeup-timeout = 999s
                                  |}
                                """.stripMargin)

    val settings = KafkaPipelineFactory().createPipelineOutletSettings(config)

    settings.groupId shouldEqual "TestGroup"
    settings.extractParallelism shouldEqual 999
    settings.eventFormat shouldEqual EventFormat.TestFormat
    settings.topics shouldEqual Some(Set("a", "b"))
    settings.topicPattern shouldEqual Some("event-*")
    settings.stopTimeout shouldEqual Some(FiniteDuration(999, "s"))
    settings.wakeupTimeout shouldEqual Some(FiniteDuration(999, "s"))

    settings.commitParallelism shouldEqual 3
    settings.commitBatchMax shouldEqual 20
    settings.maxWakeups shouldBe empty
  }
}
