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

package com.thenetcircle.event_bus.plots.kafka

import akka.NotUsed
import akka.kafka.ProducerMessage.Message
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.interface.ISink
import com.thenetcircle.event_bus.plots.kafka.extended.{KafkaKey, KafkaPartitioner}
import com.typesafe.scalalogging.StrictLogging
import org.apache.kafka.clients.producer.{ProducerConfig, ProducerRecord}

import scala.concurrent.duration.FiniteDuration

case class KafkaSinkSettings(producerSettings: ProducerSettings[ProducerKey, ProducerValue],
                             closeTimeout: Option[FiniteDuration],
                             parallelism: Option[Int])

class KafkaSink(settings: KafkaSinkSettings) extends ISink with StrictLogging {

  import KafkaSink._

  private val producerSettings = settings.producerSettings
    .withProperty(ProducerConfig.PARTITIONER_CLASS_CONFIG, classOf[KafkaPartitioner].getName)

  override def inputGraph: Flow[Event, Event, NotUsed] =
    Flow[Event]
      .map(event => {
        Message(getProducerRecordFromEvent(event), event)
      })
      // TODO: take care of Supervision of mapAsync
      .via(Producer.flow(producerSettings))
      .map(msg => msg.message.passThrough)
}

object KafkaSink {
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
