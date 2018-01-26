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

import akka.NotUsed
import akka.kafka.ProducerMessage.Message
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.misc.Util
import com.thenetcircle.event_bus.interfaces.EventStatus.Norm
import com.thenetcircle.event_bus.interfaces.{Event, EventStatus, SinkTask, SinkTaskBuilder}
import com.thenetcircle.event_bus.tasks.kafka.extended.{EventSerializer, KafkaKey, KafkaKeySerializer, KafkaPartitioner}
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}

import scala.concurrent.duration._
import scala.util.matching.Regex

case class KafkaSinkSettings(
    bootstrapServers: String,
    defaultTopic: String = "event-${app_name}-default",
    useEventGroupAsTopic: Boolean = true,
    parallelism: Int = 100,
    closeTimeout: FiniteDuration = 60.seconds,
    useDispatcher: Option[String] = None,
    properties: Map[String, String] = Map.empty
)

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

    val clientId = s"eventbus-${runningContext.getAppContext().getAppName()}"

    _producerSettings
      .withParallelism(settings.parallelism)
      .withCloseTimeout(settings.closeTimeout)
      .withProperty(ProducerConfig.PARTITIONER_CLASS_CONFIG, classOf[KafkaPartitioner].getName)
      .withBootstrapServers(settings.bootstrapServers)
    // .withProperty("client.id", clientId)
  }

  def createMessage(event: Event)(
      implicit runningContext: TaskRunningContext
  ): Message[ProducerKey, ProducerValue, Event] = {
    val record = createProducerRecord(event)
    logger.debug(s"new record $record created, going to send to kafka")
    Message(record, event)
  }

  def replaceSubstitutes(event: Event, _topic: String)(
      implicit runningContext: TaskRunningContext
  ): String = {
    var topic = _topic
    topic = topic.replaceAll(Regex.quote("""${app_name}"""), runningContext.getAppContext().getAppName())
    topic = topic.replaceAll(Regex.quote("""${app_env}"""), runningContext.getAppContext().getAppEnv())
    topic = topic.replaceAll(Regex.quote("""${story_name}"""), runningContext.getStoryName())
    topic = topic.replaceAll(Regex.quote("""${provider}"""), event.metadata.provider.map(_._2).getOrElse(""))
    topic
  }

  def createProducerRecord(event: Event)(
      implicit runningContext: TaskRunningContext
  ): ProducerRecord[ProducerKey, ProducerValue] = {
    var topic: String =
      if (settings.useEventGroupAsTopic) event.metadata.group.getOrElse(settings.defaultTopic)
      else settings.defaultTopic
    topic = replaceSubstitutes(event, topic)

    val timestamp: Long      = event.createdAt.getTime
    val key: ProducerKey     = KafkaKey(event)
    val value: ProducerValue = event

    new ProducerRecord[ProducerKey, ProducerValue](
      topic,
      null,
      timestamp.asInstanceOf[java.lang.Long],
      key,
      value
    )
  }

  var kafkaProducer: Option[KafkaProducer[ProducerKey, ProducerValue]] = None

  override def prepare()(
      implicit runningContext: TaskRunningContext
  ): Flow[Event, (EventStatus, Event), NotUsed] = {

    val kafkaSettings = getProducerSettings()

    val _kafkaProducer = kafkaProducer.getOrElse({
      kafkaProducer = Some(kafkaSettings.createKafkaProducer())
      kafkaProducer.get
    })

    // Note that the flow might be materialized multiple times, like (from HttpSource)
    Flow[Event]
      .map(createMessage)
      .via(Producer.flow(kafkaSettings, _kafkaProducer))
      .map(result => (Norm, result.message.passThrough))
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit =
    kafkaProducer.foreach(k => { k.close(5, TimeUnit.SECONDS); kafkaProducer = None })
}

class KafkaSinkBuilder() extends SinkTaskBuilder {
  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): KafkaSink = {
    val config = Util
      .convertJsonStringToConfig(configString)
      .withFallback(buildingContext.getSystemConfig().getConfig("task.kafka-sink"))

    val settings =
      KafkaSinkSettings(
        config.as[String]("bootstrap-servers"),
        config.as[String]("default-topic"),
        config.as[Boolean]("use-event-group-as-topic"),
        config.as[Int]("parallelism"),
        config.as[FiniteDuration]("close-timeout"),
        config.as[Option[String]]("use-dispatcher"),
        config.as[Map[String, String]]("properties")
      )

    new KafkaSink(settings)
  }
}
