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
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.pipeline.kafka.KafkaKey._

class KafkaKey(val rawData: String, val data: Option[KafkaKeyData]) {

  def this(data: KafkaKeyData) {
    this(packRawData(data), Some(data))
  }

  def this(rawData: String) {
    this(rawData, parseRawData(rawData))
  }

}

object KafkaKey {
  def apply(event: Event): KafkaKey =
    new KafkaKey(KafkaKeyData(event.tracingId))

  case class KafkaKeyData(tracingId: Long)

  def packRawData(data: KafkaKeyData): String = s"${data.tracingId}|"
  def parseRawData(rawData: String): Option[KafkaKeyData] =
    if (rawData.charAt(rawData.length - 1) == '|') {
      val parsed    = rawData.split('|')
      val tracingId = parsed(0).toLong
      Some(KafkaKeyData(tracingId))
    } else {
      None
    }
}
