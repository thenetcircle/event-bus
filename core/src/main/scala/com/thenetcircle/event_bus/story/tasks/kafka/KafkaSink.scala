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

package com.thenetcircle.event_bus.story.tasks.kafka

import java.util.concurrent.TimeUnit

import akka.kafka.ProducerMessage.{Envelope, Message, Result}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.AppContext
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.EventStatus.NORMAL
import com.thenetcircle.event_bus.misc.{Logging, Util}
import com.thenetcircle.event_bus.story.interfaces.{ISink, ITaskBuilder}
import com.thenetcircle.event_bus.story.tasks.kafka.extended.{
  EventSerializer,
  KafkaKey,
  KafkaKeySerializer,
  KafkaPartitioner
}
import com.thenetcircle.event_bus.story.{Payload, StoryMat, TaskRunningContext}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}

import scala.concurrent.duration._

case class KafkaSinkSettings(
    bootstrapServers: String,
    defaultTopic: String = "event-default",
    parallelism: Int = 100,
    closeTimeout: FiniteDuration = 60.seconds,
    useDispatcher: Option[String] = None,
    properties: Map[String, String] = Map.empty
)

class KafkaSink(val settings: KafkaSinkSettings) extends ISink with Logging {

  require(settings.bootstrapServers.nonEmpty, "bootstrap servers is required.")

  logger.info(s"Initializing KafkaSink with settings: $settings")

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

    val clientId = s"eventbus-${runningContext.getAppContext().getAppName()}"

    _producerSettings
      .withParallelism(settings.parallelism)
      .withCloseTimeout(settings.closeTimeout)
      .withProperty(ProducerConfig.PARTITIONER_CLASS_CONFIG, classOf[KafkaPartitioner].getName)
      .withBootstrapServers(settings.bootstrapServers)
    // .withProperty("client.id", clientId)
  }

  def createEnvelope(event: Event)(
      implicit runningContext: TaskRunningContext
  ): Envelope[ProducerKey, ProducerValue, Event] = {
    val record = createProducerRecord(event)
    producerLogger.debug(s"Prepared a new kafka record,  $record")
    Message(record, event)
  }

  def createProducerRecord(event: Event)(
      implicit runningContext: TaskRunningContext
  ): ProducerRecord[ProducerKey, ProducerValue] = {
    val topic: String        = event.metadata.topic.getOrElse(settings.defaultTopic)
    val key: ProducerKey     = KafkaKey(event)
    val value: ProducerValue = event
    // val timestamp: Long      = event.createdAt.getTime

    /*new ProducerRecord[ProducerKey, ProducerValue](
      topic,
      null,
      timestamp.asInstanceOf[java.lang.Long],
      key,
      value
    )*/

    new ProducerRecord[ProducerKey, ProducerValue](
      topic,
      key,
      value
    )
  }

  var kafkaProducer: Option[KafkaProducer[ProducerKey, ProducerValue]] = None

  override def flow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat] = {

    val kafkaSettings = getProducerSettings()

    val _kafkaProducer = kafkaProducer.getOrElse({
      logger.info("Creating a new Kafka producer")
      kafkaProducer = Some(kafkaSettings.createKafkaProducer())
      kafkaProducer.get
    })

    // Note that the flow might be materialized multiple times,
    // like from HttpSource(multiple connections), KafkaSource(multiple topicPartitions)
    // DONE issue when send to new topics, check here https://github.com/akka/reactive-kafka/issues/163
    // DONE protects that the stream crashed by sending failure
    // DONE use Producer.flexiFlow
    // DONE optimize logging
    val producingFlow = Flow[Payload]
      .map(_._2)
      .map(createEnvelope)
      .via(Producer.flexiFlow(kafkaSettings, _kafkaProducer))
      .map {
        case Result(metadata, message) =>
          val eventBrief = Util.getBriefOfEvent(message.passThrough)
          val kafkaBrief =
            s"topic: ${metadata.topic()}, partition: ${metadata.partition()}, offset: ${metadata
              .offset()}, key: ${Option(message.record.key()).map(_.rawData).getOrElse("")}"
          producerLogger.info(s"A event successfully sent to Kafka. event: $eventBrief, kafka: [$kafkaBrief]")

          (NORMAL, message.passThrough)
      }

    wrapPartialFlow(producingFlow)
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    logger.info(s"Shutting down kafka-sink of story ${getStoryName()}.")
    kafkaProducer.foreach(k => {
      k.close(5, TimeUnit.SECONDS); kafkaProducer = None
    })
  }
}

class KafkaSinkBuilder() extends ITaskBuilder[KafkaSink] {

  override val taskType: String = "kafka"

  override val defaultConfig: Config =
    ConfigFactory.parseString(
      """{
        |  # bootstrap-servers = ""
        |  default-topic = "event-default"
        |
        |  # Tuning parameter of how many sends that can run in parallel.
        |  parallelism = 100
        |
        |  # How long to wait for `KafkaProducer.close`
        |  close-timeout = 60 s
        |
        |  # Fully qualified config path which holds the dispatcher configuration
        |  # to be used by the producer stages. Some blocking may occur.
        |  # When this value is empty the dispatcher configured for the stream
        |  # will be used.
        |  use-dispatcher = "akka.kafka.default-dispatcher"
        |
        |  # Properties defined by org.apache.kafka.clients.producer.ProducerConfig
        |  # can be defined in this configuration section.
        |  properties {
        |    acks = all
        |    retries = 30
        |    "max.in.flight.requests.per.connection" = 5
        |    "enable.idempotence" = true
        |  }
        |
        |  async-buffer-size = 100
        |}""".stripMargin
    )

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): KafkaSink = {
    val settings =
      KafkaSinkSettings(
        config.as[String]("bootstrap-servers"),
        config.as[String]("default-topic"),
        config.as[Int]("parallelism"),
        config.as[FiniteDuration]("close-timeout"),
        config.as[Option[String]]("use-dispatcher"),
        config.as[Map[String, String]]("properties")
      )

    new KafkaSink(settings)
  }
}
