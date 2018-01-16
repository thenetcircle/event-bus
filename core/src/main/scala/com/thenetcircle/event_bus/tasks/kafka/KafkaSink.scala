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

import akka.{Done, NotUsed}
import akka.kafka.ProducerMessage.Message
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.interface.{SinkTask, SinkTaskBuilder}
import com.thenetcircle.event_bus.misc.ConfigStringParser
import com.thenetcircle.event_bus.story.TaskRunningContext
import com.thenetcircle.event_bus.tasks.kafka.extended.{
  EventSerializer,
  KafkaKey,
  KafkaKeySerializer,
  KafkaPartitioner
}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.clients.producer.{ProducerConfig, ProducerRecord}

import scala.concurrent.duration._
import scala.util.{Success, Try}

case class KafkaSinkSettings(bootstrapServers: String,
                             parallelism: Int = 100,
                             closeTimeout: FiniteDuration = 60.seconds,
                             properties: Map[String, String] = Map.empty)

class KafkaSink(val settings: KafkaSinkSettings) extends SinkTask with StrictLogging {

  require(settings.bootstrapServers.isEmpty, "bootstrap servers is required.")

  def getProducerSettings()(
      implicit context: TaskRunningContext
  ): ProducerSettings[ProducerKey, ProducerValue] = {
    var _producerSettings = ProducerSettings[ProducerKey, ProducerValue](
      context.getEnvironment().getConfig(),
      new KafkaKeySerializer,
      new EventSerializer
    )

    settings.properties.foreach {
      case (_key, _value) => _producerSettings = _producerSettings.withProperty(_key, _value)
    }

    _producerSettings = _producerSettings
      .withParallelism(settings.parallelism)
      .withCloseTimeout(settings.closeTimeout)
      .withProperty(ProducerConfig.PARTITIONER_CLASS_CONFIG, classOf[KafkaPartitioner].getName)
      .withBootstrapServers(settings.bootstrapServers)

    _producerSettings
  }

  def createMessage(event: Event): Message[ProducerKey, ProducerValue, Event] = {
    Message(KafkaSink.getProducerRecordFromEvent(event), event)
  }

  override def getHandler()(
      implicit context: TaskRunningContext
  ): Flow[Event, (Try[Done], Event), NotUsed] = {
    Flow[Event]
      .map(createMessage)
      // TODO: take care of Supervision of mapAsync inside flow
      .via(Producer.flow(getProducerSettings()))
      .map(result => (Success(Done), result.message.passThrough))
  }
}

object KafkaSink {
  private def getProducerRecordFromEvent(
      event: Event
  ): ProducerRecord[ProducerKey, ProducerValue] = {
    // TODO: use channel detective
    val topic: String = event.metadata.channel.getOrElse("event-default")
    val timestamp: Long = event.metadata.published
    val key: ProducerKey = KafkaKey(event)
    val value: ProducerValue = event

    new ProducerRecord[ProducerKey, ProducerValue](
      topic,
      null,
      timestamp.asInstanceOf[java.lang.Long],
      key,
      value
    )
  }
}

class KafkaSinkBuilder() extends SinkTaskBuilder {

  override def build(configString: String): KafkaSink = {
    val defaultConfig: Config = ConfigStringParser.convertStringToConfig(
      """
      |{
      |  # "bootstrap-servers": "",
      |  # Tuning parameter of how many sends that can run in parallel.
      |  "parallelism": 100,
      |  # How long to wait for `KafkaProducer.close`
      |  "close-timeout": "60s",
      |  # Properties defined by org.apache.kafka.clients.producer.ProducerConfig
      |  # can be defined in this configuration section.
      |  "properties": {}
      |}
      """.stripMargin
    )

    val config = ConfigStringParser.convertStringToConfig(configString).withFallback(defaultConfig)

    val settings =
      KafkaSinkSettings(
        config.as[String]("bootstrap-servers"),
        config.as[Int]("parallelism"),
        config.as[FiniteDuration]("close-timeout"),
        config.as[Map[String, String]]("properties")
      )

    new KafkaSink(settings)
  }

}
