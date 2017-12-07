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

import akka.NotUsed
import akka.kafka.ProducerMessage.Message
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.pipeline.kafka.extended.KafkaPartitioner
import com.thenetcircle.event_bus.pipeline.model.{PipelineInlet, PipelineInletSettings}
import com.thenetcircle.event_bus.tracing.{Tracing, TracingSteps}
import org.apache.kafka.clients.producer.{ProducerConfig, ProducerRecord}

import scala.concurrent.duration.FiniteDuration

// TODO: When Kafka delivery failed, Which will complete upstreams as well, Can be reproduced by create new topic
private[kafka] final class KafkaPipelineInlet(val pipeline: KafkaPipeline,
                                              val name: String,
                                              val settings: KafkaPipelineInletSettings)
    extends PipelineInlet
    with Tracing {

  import KafkaPipelineInlet._

  // Combine LeftPortSettings with PipelineSettings
  private val producerSettings: ProducerSettings[ProducerKey, ProducerValue] = {
    val _settings = pipeline.settings.producerSettings
      .withProperty(ProducerConfig.PARTITIONER_CLASS_CONFIG, classOf[KafkaPartitioner].getName)

    settings.closeTimeout.foreach(_settings.withCloseTimeout)
    settings.parallelism.foreach(_settings.withParallelism)
    _settings
  }

  override def stream(): Flow[Event, Event, NotUsed] =
    Flow[Event]
      .via(tracingFlow(TracingSteps.PIPELINE_PUSHING))
      .map(event => {
        Message(getProducerRecordFromEvent(event), event)
      })
      // TODO: take care of Supervision of mapAsync
      .via(Producer.flow(producerSettings))
      .map(msg => msg.message.passThrough)
      .via(tracingFlow(TracingSteps.PIPELINE_PUSHED))
      .named(name)
}

object KafkaPipelineInlet {
  private def getProducerRecordFromEvent(
      event: Event
  ): ProducerRecord[ProducerKey, ProducerValue] = {
    val topic: String = event.channel
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

case class KafkaPipelineInletSettings(closeTimeout: Option[FiniteDuration],
                                      parallelism: Option[Int])
    extends PipelineInletSettings
