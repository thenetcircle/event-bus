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

package com.thenetcircle.event_bus.driver.adapter

import akka.util.ByteString
import com.thenetcircle.event_bus.{ EventSource, RawEvent }
import com.thenetcircle.event_bus.driver.{ KafkaKey, KafkaValue }
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord

object KafkaSourceAdapter extends SourceAdapter[ConsumerRecord[KafkaKey, KafkaValue]] {
  override def fit(message: ConsumerRecord[KafkaKey, KafkaValue]): RawEvent =
    RawEvent(
      ByteString(message.value()),
      message.topic(),
      Map("key" -> ByteString(message.key()),
          "offset" -> message.offset(),
          "timestamp" -> message.timestamp(),
          "partition" -> message.partition()),
      EventSource.Kafka
    )
}

object KafkaSinkAdapter extends SinkAdapter[ProducerRecord[KafkaKey, KafkaValue]] {
  override def unfit(event: RawEvent): ProducerRecord[KafkaKey, KafkaValue] = {
    val context = event.context

    val topic = event.channel
    val value = event.body.toArray
    val partition = context.get("partition") match {
      case Some(p: Int) => p
      case _ => null
    }
    val timestamp = context.get("timestamp") match {
      case Some(t: Long) => t
      case _ => null
    }
    val key = context.get("key") match {
      case Some(k: ByteString) => k.toArray
      case Some(k: String) => k.getBytes("UTF-8")
      case Some(k: KafkaKey) => k
      case _ => null
    }

    new ProducerRecord[KafkaKey, KafkaValue](topic,
                                             partition.asInstanceOf[java.lang.Integer],
                                             timestamp.asInstanceOf[java.lang.Long],
                                             key,
                                             value)
  }

}
