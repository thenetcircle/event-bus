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

import akka.NotUsed
import akka.kafka.ProducerMessage.{Envelope, Message, Result}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.AppContext
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.EventStatus.{NORMAL, STAGED, STAGING}
import com.thenetcircle.event_bus.story.interfaces._
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
    clientSettings: KafkaSinkClientSettings
)

case class KafkaSinkClientSettings(
    parallelism: Option[Int] = None,
    closeTimeout: Option[FiniteDuration] = None,
    useDispatcher: Option[String] = None,
    properties: Map[String, String] = Map.empty
)

class KafkaSink(val settings: KafkaSinkSettings) extends ISink with IFailoverTask with ITaskLogging {

  require(settings.bootstrapServers.nonEmpty, "bootstrap servers is required.")

  logger.info(s"Initializing KafkaSink with settings: $settings")

  def getProducerSettings()(
      implicit runningContext: TaskRunningContext
  ): ProducerSettings[ProducerKey, ProducerValue] = {
    var producerSettings = ProducerSettings[ProducerKey, ProducerValue](
      runningContext.getActorSystem(),
      new KafkaKeySerializer,
      new EventSerializer
    )
    val clientSettings = settings.clientSettings

    clientSettings.properties.foreach {
      case (_key, _value) => producerSettings = producerSettings.withProperty(_key, _value)
    }

    clientSettings.parallelism.foreach(dp => producerSettings = producerSettings.withParallelism(dp))
    clientSettings.closeTimeout.foreach(dp => producerSettings = producerSettings.withCloseTimeout(dp))
    clientSettings.useDispatcher.foreach(dp => producerSettings = producerSettings.withDispatcher(dp))

    // val clientId = s"eventbus-${runningContext.getAppContext().getAppName()}"

    producerSettings
      .withProperty(ProducerConfig.PARTITIONER_CLASS_CONFIG, classOf[KafkaPartitioner].getName)
      .withBootstrapServers(settings.bootstrapServers)
    // .withProperty("client.id", clientId)
  }

  def createEnvelope(event: Event)(
      implicit runningContext: TaskRunningContext
  ): Envelope[ProducerKey, ProducerValue, Event] = {
    val record = createProducerRecord(event)
    taskLogger.debug(s"Prepared a new kafka record: $record from event: ${event.summary}")
    Message(record, event)
  }

  def getDefaultTopic()(
      implicit runningContext: TaskRunningContext
  ): String =
    replaceKafkaTopicSubstitutes(settings.defaultTopic)

  def createProducerRecord(event: Event)(
      implicit runningContext: TaskRunningContext
  ): ProducerRecord[ProducerKey, ProducerValue] = {
    val topic: String        = event.metadata.topic.getOrElse(getDefaultTopic())
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

  private def getProducingFlow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, NotUsed] = {
    val kafkaSettings = getProducerSettings()

    val _kafkaProducer = kafkaProducer.getOrElse({
      taskLogger.info(s"Creating a new Kafka producer")
      kafkaProducer = Some(kafkaSettings.createKafkaProducer())
      kafkaProducer.get
    })

    // Note that the flow might be materialized multiple times,
    // like from HttpSource(multiple connections), KafkaSource(multiple topicPartitions)
    // DONE issue when send to new topics, check here https://github.com/akka/reactive-kafka/issues/163
    // DONE protects that the stream crashed by sending failure
    // DONE use Producer.flexiFlow
    // DONE optimize logging
    Flow[Payload]
      .map(_._2)
      .map(createEnvelope)
      .via(Producer.flexiFlow(kafkaSettings, _kafkaProducer))
      .map {
        case Result(metadata, message) =>
          val kafkaBrief =
            s"topic: ${metadata.topic()}, partition: ${metadata.partition()}, offset: ${metadata
              .offset()}, key: ${Option(message.record.key()).map(_.rawData).getOrElse("")}"
          taskLogger.info(
            s"A event successfully sent to Kafka. event: ${message.passThrough.summary}, kafka: [$kafkaBrief]"
          )

          (NORMAL, message.passThrough)
      }
  }

  override def sinkFlow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat] =
    ITask.wrapPartialFlow(getProducingFlow())

  override def failoverFlow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat] =
    ITask.wrapPartialFlow(
      getProducingFlow().map { case (_, event) => (STAGED, event) },
      { case (_: STAGING, _) => true }
    )

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    taskLogger.info(s"Shutting down Kafka Sink.")
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
        |  default-topic = "event-v2-{app_name}{app_env}-default"
        |
        |  akka-kafka {
        |    # parallelism = 100
        |    # close-timeout = 60 s
        |    # use-dispatcher = "akka.kafka.default-dispatcher"
        |  }
        |
        |  # Properties defined by org.apache.kafka.clients.producer.ProducerConfig
        |  properties {
        |    acks = all
        |    retries = 30
        |    "max.in.flight.requests.per.connection" = 5
        |    "enable.idempotence" = true
        |  }
        |}""".stripMargin
    )

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): KafkaSink = {
    val akkaKafkaConfig = config.getConfig("akka-kafka")
    val settings =
      KafkaSinkSettings(
        config.as[String]("bootstrap-servers"),
        config.as[String]("default-topic"),
        KafkaSinkClientSettings(
          akkaKafkaConfig.as[Option[Int]]("parallelism"),
          akkaKafkaConfig.as[Option[FiniteDuration]]("close-timeout"),
          akkaKafkaConfig.as[Option[String]]("use-dispatcher"),
          config.as[Map[String, String]]("properties")
        )
      )
    new KafkaSink(settings)
  }
}
