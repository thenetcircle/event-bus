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
import com.thenetcircle.event_bus.pipeline.kafka.KafkaPipeline.{Key, Value}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.common.serialization.{
  ByteArrayDeserializer,
  ByteArraySerializer
}

private[pipeline] class KafkaPipelineFactory(configPool: PipelineConfigPool)(
    implicit system: ActorSystem)
    extends PipelineFactory {

  override def getPipelineSettings(
      pipelineName: String): KafkaPipelineSettings = {
    val pipelineTypeOption   = configPool.getPipelineType(pipelineName)
    val pipelineConfigOption = configPool.getPipelineConfig(pipelineName)

    require(pipelineTypeOption.isDefined,
            s"Type of pipeline $pipelineName doesn't defined")
    require(pipelineConfigOption.isDefined,
            s"Config of pipeline $pipelineName doesn't defined")
    require(pipelineTypeOption.get == PipelineType.Kafka,
            "KafkaPipelineFactory can only handler Kafka Pipeline")

    val pipelineConfig = pipelineConfigOption.get

    val originalProducerConfig =
      system.settings.config.getConfig("akka.kafka.producer")
    val defaultProducerConfig = {
      if (pipelineConfig.hasPath("producer"))
        pipelineConfig
          .getConfig("producer")
          .withFallback(originalProducerConfig)
      else
        originalProducerConfig
    }

    val originalConsumerConfig =
      system.settings.config.getConfig("akka.kafka.consumer")
    val defaultConsumerConfig = {
      if (pipelineConfig.hasPath("consumer"))
        pipelineConfig
          .getConfig("consumer")
          .withFallback(originalConsumerConfig)
      else
        originalConsumerConfig
    }

    KafkaPipelineSettings(pipelineName,
                          defaultProducerConfig,
                          defaultConsumerConfig)
  }

  override def getPipeline(pipelineName: String): KafkaPipeline =
    getCache(pipelineName) match {
      case Some(pipeline: KafkaPipeline) => pipeline
      case None                          => createPipeline(pipelineName)
      case Some(_: Pipeline)             => throw new Exception("Unexpected pipeline type.")
    }

  protected def createPipeline(pipelineName: String): KafkaPipeline = {
    val pipelineSettings = getPipelineSettings(pipelineName)
    val pipeline         = KafkaPipeline(pipelineSettings)
    addToCache(pipelineName, pipeline)
    pipeline
  }

  override def getPipelineInletSettings(
      pipelineName: String,
      pipelineInletConfig: Config): KafkaPipelineInletSettings = {

    val pipelineSettings = getPipelineSettings(pipelineName)
    val producerConfig =
      if (pipelineInletConfig.hasPath("producer"))
        pipelineInletConfig
          .getConfig("producer")
          .withFallback(pipelineSettings.defaultProducerConfig)
      else
        pipelineSettings.defaultProducerConfig

    KafkaPipelineInletSettings(
      producerSettings = ProducerSettings[Key, Value](producerConfig,
                                                      new ByteArraySerializer,
                                                      new ByteArraySerializer)
    )
  }

  override def getPipelineOutletSettings(
      pipelineName: String,
      pipelineOutletConfig: Config): KafkaPipelineOutletSettings = {
    val pipelineSettings = getPipelineSettings(pipelineName)
    val consumerConfig =
      if (pipelineOutletConfig.hasPath("consumer"))
        pipelineOutletConfig
          .getConfig("consumer")
          .withFallback(pipelineSettings.defaultConsumerConfig)
      else
        pipelineSettings.defaultConsumerConfig

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
      consumerSettings = ConsumerSettings[Key, Value](consumerConfig,
                                                      new ByteArrayDeserializer,
                                                      new ByteArrayDeserializer)
    )
  }

}

object KafkaPipelineFactory {
  private var cached: Option[KafkaPipelineFactory] = None
  def apply()(implicit system: ActorSystem): KafkaPipelineFactory =
    cached match {
      case Some(f) => f
      case None =>
        val f = new KafkaPipelineFactory(PipelineConfigPool())
        cached = Some(f)
        f
    }
}
