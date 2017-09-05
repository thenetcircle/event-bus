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
import akka.util.ByteString
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.pipeline.LeftPort
import com.thenetcircle.event_bus.pipeline.kafka.KafkaPipeline.{Key, Value}
import org.apache.kafka.clients.producer.ProducerRecord

/** LeftPort Implementation */
private[kafka] final class KafkaLeftPort(
    name: String,
    pipelineSettings: KafkaPipelineSettings,
    settings: KafkaLeftPortSettings)
    extends LeftPort {

  import KafkaPipeline._
  import KafkaLeftPort._

  override val port: Flow[Event, Event, NotUsed] = {

    // Combine LeftPortSettings with PipelineSettings
    val producerSettings: ProducerSettings[Key, Value] = {
      var result = pipelineSettings.producerSettings

      settings.properties.foreach(properties =>
        properties foreach {
          case (key, value) =>
            result = result.withProperty(key, value)
      })
      settings.closeTimeout.foreach(s => result = result.withCloseTimeout(s))
      settings.produceParallelism.foreach(s =>
        result = result.withParallelism(s))
      settings.dispatcher.foreach(s => result = result.withDispatcher(s))

      result
    }

    Flow[Event]
      .map(
        event =>
          Message(
            getProducerRecordFromEvent(event),
            event
        )
      )
      // TODO: take care of Supervision of mapAsync
      .via(Producer.flow(producerSettings))
      .map(_.message.passThrough)
      .named(name)

  }
}

object KafkaLeftPort {
  // TODO: manually calculate partition, use key for metadata
  private def getProducerRecordFromEvent(
      event: Event): ProducerRecord[Key, Value] = {
    val topic: String   = event.channel
    val timestamp: Long = event.metadata.timestamp
    val key: Key        = getKeyFromEvent(event)
    val value: Value    = event.body.data.toArray

    new ProducerRecord[Key, Value](topic,
                                   null,
                                   timestamp.asInstanceOf[java.lang.Long],
                                   key,
                                   value)
  }

  private def getKeyFromEvent(event: Event): Key =
    ByteString(s"${event.metadata.trigger._1}#${event.metadata.trigger._2}").toArray
}
