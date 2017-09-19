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
import com.thenetcircle.event_bus.pipeline.PipelineInlet
import com.thenetcircle.event_bus.pipeline.kafka.extended.KafkaPartitioner
import com.thenetcircle.event_bus.tracing.Tracing
import org.apache.kafka.clients.producer.{ProducerConfig, ProducerRecord}

/** LeftPort Implementation */
private[kafka] final class KafkaPipelineInlet(
    val pipeline: KafkaPipeline,
    val inletName: String,
    val inletSettings: KafkaPipelineInletSettings)
    extends PipelineInlet
    with Tracing {

  import KafkaPipelineInlet._

  private def tracingFlow(text: String): Flow[Event, Event, NotUsed] =
    Flow[Event].map(event => {
      tracer.record(event, text)
      event
    })

  override val stream: Flow[Event, Event, NotUsed] = {

    // Combine LeftPortSettings with PipelineSettings
    val producerSettings: ProducerSettings[ProducerKey, ProducerValue] = {
      val _settings = pipeline.pipelineSettings.producerSettings.withProperty(
        ProducerConfig.PARTITIONER_CLASS_CONFIG,
        classOf[KafkaPartitioner].getName)

      inletSettings.closeTimeout.foreach(_settings.withCloseTimeout)
      inletSettings.parallelism.foreach(_settings.withParallelism)
      _settings
    }

    Flow[Event]
      .via(tracingFlow("Before Pipeline"))
      .map(
        event => {
          Message(
            getProducerRecordFromEvent(event),
            event
          )
        }
      )
      // TODO: take care of Supervision of mapAsync
      .via(Producer.flow(producerSettings))
      .map(_.message.passThrough)
      .via(tracingFlow("After Pipeline"))
      .named(inletName)
  }
}

object KafkaPipelineInlet {
  private def getProducerRecordFromEvent(
      event: Event): ProducerRecord[ProducerKey, ProducerValue] = {
    val topic: String        = event.channel
    val timestamp: Long      = event.metadata.timestamp
    val key: ProducerKey     = KafkaKey(event)
    val value: ProducerValue = event

    new ProducerRecord[ProducerKey, ProducerValue](
      topic,
      null,
      timestamp.asInstanceOf[java.lang.Long],
      key,
      value)
  }
}
