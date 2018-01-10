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

import akka.kafka.ConsumerSettings
import com.thenetcircle.event_bus.RunningContext
import com.thenetcircle.event_bus.interface.ISourceBuilder
import com.thenetcircle.event_bus.plots.kafka.extended.KafkaKeyDeserializer
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.common.serialization.ByteArrayDeserializer

class KafkaSourceBuilder() extends ISourceBuilder {

  val defaultConfig: Config = convertStringToConfig(
    """
      |{
      |  # "group-id": "...",
      |  # "topics": [],
      |  # "topic-pattern": "event-*", # supports wildcard
      |
      |  "extract-parallelism": 3,
      |  "commit-parallelism": 3,
      |  "commit-batch-max": 20,
      |  "max-partitions": 1000,
      |
      |  "consumer": {
      |    # Tuning property of scheduled polls.
      |    "poll-interval": "50ms",
      |
      |    # Tuning property of the `KafkaConsumer.poll` parameter.
      |    # Note that non-zero value means that blocking of the thread that
      |    # is executing the stage will be blocked.
      |    "poll-timeout": "50ms",
      |
      |    # The stage will be await outstanding offset commit requests before
      |    # shutting down, but if that takes longer than this timeout it will
      |    # stop forcefully.
      |    "stop-timeout": "30s",
      |
      |    # How long to wait for `KafkaConsumer.close`
      |    "close-timeout": "20s",
      |
      |    # If offset commit requests are not completed within this timeout
      |    # the returned Future is completed `TimeoutException`.
      |    "commit-timeout": "15s",
      |
      |    # If the KafkaConsumer can't connect to the broker the poll will be
      |    # aborted after this timeout. The KafkaConsumerActor will throw
      |    # org.apache.kafka.common.errors.WakeupException which will be ignored
      |    # until max-wakeups limit gets exceeded.
      |    "wakeup-timeout": "3s",
      |
      |    # After exceeding maxinum wakeups the consumer will stop and the stage will fail.
      |    "max-wakeups": 10,
      |
      |    # Fully qualified config path which holds the dispatcher configuration
      |    # to be used by the KafkaConsumerActor. Some blocking may occur.
      |    "use-dispatcher": "akka.kafka.default-dispatcher",
      |
      |    # Properties defined by org.apache.kafka.clients.consumer.ConsumerConfig
      |    # can be defined in this configuration section.
      |    "kafka-clients": {
      |      "client": { "id": "EventBus-Consumer" },
      |
      |      # Disable auto-commit by default
      |      "enable": { "auto": { "commit": false } }
      |    }
      |  }
      |}
    """.stripMargin
  )

  override def build(configString: String)(implicit runningContext: RunningContext): KafkaSource = {

    val config = convertStringToConfig(configString).withFallback(defaultConfig)

    val consumerConfig = config
      .getConfig("consumer")
      .withFallback(runningContext.environment.getConfig().getConfig("akka.kafka.consumer"))

    val settings = KafkaSourceSettings(
      groupId = config.as[String]("group-id"),
      extractParallelism = config.as[Int]("extract-parallelism"),
      commitParallelism = config.as[Int]("commit-parallelism"),
      commitBatchMax = config.as[Int]("commit-batch-max"),
      maxPartitions = config.as[Int]("max-partitions"),
      consumerSettings = ConsumerSettings[ConsumerKey, ConsumerValue](
        consumerConfig,
        new KafkaKeyDeserializer,
        new ByteArrayDeserializer
      ),
      topics = config.as[Option[Set[String]]]("topics"),
      topicPattern = config.as[Option[String]]("topic-pattern")
    )

    new KafkaSource(settings)

  }

}
