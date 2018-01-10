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
import akka.kafka.ProducerSettings
import com.thenetcircle.event_bus.RunningContext
import com.thenetcircle.event_bus.interface.ISinkBuilder
import com.thenetcircle.event_bus.plots.kafka.extended.{EventSerializer, KafkaKeySerializer}
import com.typesafe.config.Config

class KafkaSinkBuilder() extends ISinkBuilder {

  val defaultConfig: Config = convertStringToConfig(
    """
      |{
      |  "producer": {
      |    # Tuning parameter of how many sends that can run in parallel.
      |    "parallelism": 100,
      |
      |    # How long to wait for `KafkaProducer.close`
      |    "close-timeout": "60s",
      |
      |    # Fully qualified config path which holds the dispatcher configuration
      |    # to be used by the producer stages. Some blocking may occur.
      |    # When this value is empty, the dispatcher configured for the stream
      |    # will be used.
      |    "use-dispatcher": "akka.kafka.default-dispatcher",
      |
      |    # Properties defined by org.apache.kafka.clients.producer.ProducerConfig
      |    # can be defined in this configuration section.
      |    "kafka-clients": {
      |      "client": { "id": "EventBus-Producer" }
      |    }
      |  }
      |}
    """.stripMargin
  )

  override def build(configString: String)(implicit runningContext: RunningContext): KafkaSink = {

    val config = convertStringToConfig(configString).withFallback(defaultConfig)

    val producerConfig = config
      .getConfig("producer")
      .withFallback(runningContext.environment.getConfig().getConfig("akka.kafka.producer"))

    val sinkSettings = KafkaSinkSettings(
      ProducerSettings[ProducerKey, ProducerValue](
        producerConfig,
        new KafkaKeySerializer,
        new EventSerializer
      )
    )

    new KafkaSink(sinkSettings)
  }

}
