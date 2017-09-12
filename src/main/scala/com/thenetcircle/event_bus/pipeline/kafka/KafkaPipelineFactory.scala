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

import akka.kafka.{ConsumerSettings, ProducerSettings}
import com.thenetcircle.event_bus.EventFormat
import com.thenetcircle.event_bus.pipeline._
import com.thenetcircle.event_bus.pipeline.kafka.KafkaPipeline.{Key, Value}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.common.serialization.{
  ByteArrayDeserializer,
  ByteArraySerializer
}

import scala.concurrent.duration.FiniteDuration

object KafkaPipelineFactory extends PipelineFactory {

  override def createPipeline(
      pipelineSettings: PipelineSettings): KafkaPipeline =
    KafkaPipeline(pipelineSettings.asInstanceOf[KafkaPipelineSettings])

  override def createPipelineSettings(
      pipelineConfig: Config): KafkaPipelineSettings = {
    val originalProducerConfig =
      system.settings.config.getConfig("akka.kafka.producer")
    val producerConfig = {
      if (pipelineConfig.hasPath("producer"))
        pipelineConfig
          .getConfig("producer")
          .withFallback(originalProducerConfig)
      else
        originalProducerConfig
    }

    val originalConsumerConfig =
      system.settings.config.getConfig("akka.kafka.consumer")
    val consumerConfig = {
      if (pipelineConfig.hasPath("consumer"))
        pipelineConfig
          .getConfig("consumer")
          .withFallback(originalConsumerConfig)
      else
        originalConsumerConfig
    }

    KafkaPipelineSettings(
      pipelineConfig.as[String]("name"),
      ProducerSettings[Key, Value](producerConfig,
                                   new ByteArraySerializer,
                                   new ByteArraySerializer),
      ConsumerSettings[Key, Value](consumerConfig,
                                   new ByteArrayDeserializer,
                                   new ByteArrayDeserializer)
    )
  }

  override def createPipelineInletSettings(
      pipelineInletConfig: Config): KafkaPipelineInletSettings = {
    val config = pipelineInletConfig.withFallback(
      system.settings.config
        .as[Config]("event-bus.pipeline.default.kafka.inlet"))

    KafkaPipelineInletSettings(
      closeTimeout = config.as[Option[FiniteDuration]]("close-timeout"),
      parallelism = config.as[Option[Int]]("parallelism")
    )
  }

  override def createPipelineOutletSettings(
      pipelineOutletConfig: Config): KafkaPipelineOutletSettings = {
    val config = pipelineOutletConfig.withFallback(
      system.settings.config
        .as[Config]("event-bus.pipeline.default.kafka.outlet"))

    KafkaPipelineOutletSettings(
      groupId = config.as[String]("group-id"),
      extractParallelism = config.as[Int]("extract-parallelism"),
      commitParallelism = config.as[Int]("commit-parallelism"),
      commitBatchMax = config.as[Int]("commit-batch-max"),
      eventFormat = config
        .as[Option[EventFormat]]("event-format")
        .getOrElse(EventFormat.DefaultFormat),
      topics = config.as[Option[Set[String]]]("topics"),
      topicPattern = config.as[Option[String]]("topicPattern"),
      pollInterval = config.as[Option[FiniteDuration]]("poll-interval"),
      pollTimeout = config.as[Option[FiniteDuration]]("poll-timeout"),
      stopTimeout = config.as[Option[FiniteDuration]]("stop-timeout"),
      closeTimeout = config.as[Option[FiniteDuration]]("close-timeout"),
      commitTimeout = config.as[Option[FiniteDuration]]("commit-timeout"),
      wakeupTimeout = config.as[Option[FiniteDuration]]("wakeup-timeout"),
      maxWakeups = config.as[Option[Int]]("max-wakeups")
    )
  }

}

/*object KafkaPipelineFactory {
  private var cached: Option[KafkaPipelineFactory] = None
  def apply()(implicit system: ActorSystem): KafkaPipelineFactory =
    cached match {
      case Some(f) => f
      case None =>
        val f = new KafkaPipelineFactory(PipelinePool())
        cached = Some(f)
        f
    }
}*/
