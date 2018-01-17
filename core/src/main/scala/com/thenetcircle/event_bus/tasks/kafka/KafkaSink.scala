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

import java.util.concurrent.TimeUnit

import akka.kafka.ProducerMessage.Message
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.helper.ConfigStringParser
import com.thenetcircle.event_bus.interface.{SinkTask, SinkTaskBuilder}
import com.thenetcircle.event_bus.tasks.kafka.extended.{
  EventSerializer,
  KafkaKey,
  KafkaKeySerializer,
  KafkaPartitioner
}
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.clients.producer.{ProducerConfig, ProducerRecord}

import scala.concurrent.duration._
import scala.util.{Success, Try}

case class KafkaSinkSettings(bootstrapServers: String,
                             defaultTopic: String = "event-default",
                             useEventChannel: Boolean = true,
                             parallelism: Int = 100,
                             closeTimeout: FiniteDuration = 60.seconds,
                             useDispatcher: Option[String] = None,
                             properties: Map[String, String] = Map.empty)

class KafkaSink(val settings: KafkaSinkSettings) extends SinkTask with StrictLogging {

  require(settings.bootstrapServers.nonEmpty, "bootstrap servers is required.")

  def getProducerSettings()(
      implicit runningContext: TaskRunningContext
  ): ProducerSettings[ProducerKey, ProducerValue] = {
    var _producerSettings = ProducerSettings[ProducerKey, ProducerValue](
      runningContext.getActorSystem(),
      new KafkaKeySerializer,
      new EventSerializer
    )

    settings.properties.foreach {
      case (_key, _value) => _producerSettings = _producerSettings.withProperty(_key, _value)
    }

    settings.useDispatcher.foreach(dp => _producerSettings = _producerSettings.withDispatcher(dp))

    _producerSettings = _producerSettings
      .withParallelism(settings.parallelism)
      .withCloseTimeout(settings.closeTimeout)
      .withProperty(ProducerConfig.PARTITIONER_CLASS_CONFIG, classOf[KafkaPartitioner].getName)
      .withBootstrapServers(settings.bootstrapServers)

    _producerSettings
  }

  def createMessage(event: Event): Message[ProducerKey, ProducerValue, Event] = {
    Message(createProducerRecord(event), event)
  }

  def createProducerRecord(event: Event): ProducerRecord[ProducerKey, ProducerValue] = {
    val topic: String =
      if (settings.useEventChannel) event.metadata.channel.getOrElse(settings.defaultTopic)
      else settings.defaultTopic
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

  override def getHandler()(
      implicit runningContext: TaskRunningContext
  ): Flow[Event, (Try[Done], Event), NotUsed] = {

    val kafkaSettings = getProducerSettings()
    lazy val kafkaProducer = kafkaSettings.createKafkaProducer()

    runningContext.addShutdownHook(kafkaProducer.close(10, TimeUnit.SECONDS))

    // Note that the flow might be materialized multiple times, like (from HttpSource)
    Flow[Event]
      .map(createMessage)
      // TODO: take care of Supervision of mapAsync inside flow
      .via(Producer.flow(kafkaSettings, kafkaProducer))
      .map(result => (Success(Done), result.message.passThrough))
  }
}

class KafkaSinkBuilder() extends SinkTaskBuilder {
  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): KafkaSink = {
    val config = ConfigStringParser
      .convertStringToConfig(configString)
      .withFallback(buildingContext.getSystemConfig().getConfig("task.kafka-sink"))

    val settings =
      KafkaSinkSettings(
        config.as[String]("bootstrap-servers"),
        config.as[String]("default-topic"),
        config.as[Boolean]("use-event-channel"),
        config.as[Int]("parallelism"),
        config.as[FiniteDuration]("close-timeout"),
        config.as[Option[String]]("use-dispatcher"),
        config.as[Map[String, String]]("properties")
      )

    new KafkaSink(settings)
  }
}
