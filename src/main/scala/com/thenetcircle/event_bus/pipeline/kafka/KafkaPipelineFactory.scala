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

import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, ProducerSettings}
import com.thenetcircle.event_bus.EventFormat
import com.thenetcircle.event_bus.pipeline._
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.common.serialization.{
  ByteArrayDeserializer,
  ByteArraySerializer
}

import scala.concurrent.duration.FiniteDuration

final class KafkaPipelineFactory(pipelineConfigFactory: PipelineConfigFactory)
    extends PipelineFactory {

  override def getPipelineSettings(pipelineName: String)(
      implicit system: ActorSystem): KafkaPipelineSettings = {

    require(pipelineConfigFactory
              .getPipelineType(pipelineName)
              .get == PipelineType.Kafka,
            "KafkaPipelineFactory can only handler Kafka Pipeline")

    val pipelineConfig =
      pipelineConfigFactory.getPipelineConfig(pipelineName).get

    val originalProducerConfig =
      system.settings.config.getConfig("akka.kafka.producer")
    val producerConfig = {
      if (pipelineConfig.hasPath("akka.kafka.producer"))
        pipelineConfig
          .getConfig("akka.kafka.producer")
          .withFallback(originalProducerConfig)
      else
        originalProducerConfig
    }

    val originalConsumerConfig =
      system.settings.config.getConfig("akka.kafka.consumer")
    val consumerConfig = {
      if (pipelineConfig.hasPath("akka.kafka.consumer"))
        pipelineConfig
          .getConfig("akka.kafka.consumer")
          .withFallback(originalConsumerConfig)
      else
        originalConsumerConfig
    }

    KafkaPipelineSettings(
      pipelineName,
      ProducerSettings[KafkaPipeline.Key, KafkaPipeline.Value](
        producerConfig,
        new ByteArraySerializer,
        new ByteArraySerializer),
      ConsumerSettings[KafkaPipeline.Key, KafkaPipeline.Value](
        consumerConfig,
        new ByteArrayDeserializer,
        new ByteArrayDeserializer)
    )
  }

  override def getPipeline(pipelineName: String)(
      implicit system: ActorSystem): KafkaPipeline =
    getCachedPipeline(pipelineName) match {
      case Some(pipeline: KafkaPipeline) => pipeline
      case Some(_) =>
        throw new Exception(
          s"""The type of pipeline "$pipelineName" is not correct""")
      case None => createKafkaPipeline(pipelineName)
    }

  protected def createKafkaPipeline(pipelineName: String)(
      implicit system: ActorSystem): KafkaPipeline = {
    val pipelineSettings = getPipelineSettings(pipelineName)
    val pipeline         = KafkaPipeline(pipelineSettings)
    addToCachedPipeline(pipelineName, pipeline)
    pipeline
  }

  override def getPipelineInletSettings(
      pipelineInletConfig: Config): KafkaPipelineInletSettings = {
    /*val properties: Option[Map[String, String]] = {
      if (leftPortConfig.hasPath("properties"))
        Some(
          leftPortConfig
            .getObject("properties")
            .unwrapped()
            .asInstanceOf[java.util.Map[String, String]]
            .asScala
            .toMap)
      else
        None
    }*/

    KafkaPipelineInletSettings(
      produceParallelism =
        pipelineInletConfig.as[Option[Int]]("produce-parallelism"),
      dispatcher = pipelineInletConfig.as[Option[String]]("dispatcher"),
      properties =
        pipelineInletConfig.as[Option[Map[String, String]]]("properties"),
      closeTimeout =
        pipelineInletConfig.as[Option[FiniteDuration]]("close-timeout")
    )
  }

  override def getPipelineOutletSettings(
      pipelineOutletConfig: Config): KafkaPipelineOutletSettings = {
    KafkaPipelineOutletSettings(
      groupId = pipelineOutletConfig.as[String]("group-id"),
      extractParallelism = pipelineOutletConfig.as[Int]("extract-parallelism"),
      commitParallelism = pipelineOutletConfig.as[Int]("commit-parallelism"),
      commitBatchMax = pipelineOutletConfig.as[Int]("commit-batch-max"),
      eventFormat = pipelineOutletConfig
        .as[Option[EventFormat]]("event-format")
        .getOrElse(EventFormat.DefaultFormat),
      topics = pipelineOutletConfig.as[Option[Set[String]]]("topics"),
      topicPattern = pipelineOutletConfig.as[Option[String]]("topicPattern"),
      dispatcher = pipelineOutletConfig.as[Option[String]]("dispatcher"),
      properties =
        pipelineOutletConfig.as[Option[Map[String, String]]]("properties"),
      pollInterval =
        pipelineOutletConfig.as[Option[FiniteDuration]]("poll-interval"),
      pollTimeout =
        pipelineOutletConfig.as[Option[FiniteDuration]]("poll-timeout"),
      stopTimeout =
        pipelineOutletConfig.as[Option[FiniteDuration]]("stop-timeout"),
      closeTimeout =
        pipelineOutletConfig.as[Option[FiniteDuration]]("close-timeout"),
      commitTimeout =
        pipelineOutletConfig.as[Option[FiniteDuration]]("commit-timeout"),
      wakeupTimeout =
        pipelineOutletConfig.as[Option[FiniteDuration]]("wakeup-timeout"),
      maxWakeups = pipelineOutletConfig.as[Option[Int]]("max-wakeups")
    )
  }

}

object KafkaPipelineFactory {
  private var cached: Option[KafkaPipelineFactory] = None
  def apply(): KafkaPipelineFactory = cached match {
    case Some(factory) => factory
    case None =>
      val factory = new KafkaPipelineFactory(PipelineConfigFactory())
      cached = Some(factory)
      factory
  }
}
