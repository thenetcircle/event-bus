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

package com.thenetcircle.event_bus.tasks.kafka.extended
import com.thenetcircle.event_bus.event.extractor.DataFormat
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.thenetcircle.event_bus.interfaces.Event
import com.thenetcircle.event_bus.tasks.kafka.extended.KafkaKey._

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
    new KafkaKey(KafkaKeyData(event.body.format, None))

  /** Serializes [[KafkaKeyData]]
   * @param data
   * @return the serialized value
   */
  def packRawData(data: KafkaKeyData): String =
    s"${data.eventFormat.toString}|${data.tracingId.getOrElse("")}|"

  /** Deserializes rawdata to be [[KafkaKeyData]]
   * @param rawData
   * @return [[KafkaKeyData]]
   */
  def parseRawData(rawData: String): Option[KafkaKeyData] =
    if (rawData.charAt(rawData.length - 1) == '|') {
      val parsed = rawData.split('|')
      val eventFormat = DataFormat(parsed(0))

      val tracingId =
        if (parsed.isDefinedAt(1) && parsed(1) != "") Some(parsed(1).toLong)
        else None

      Some(KafkaKeyData(eventFormat, tracingId))
    } else {
      None
    }

  /** Holds real data of the key of a kafka message */
  case class KafkaKeyData(eventFormat: DataFormat, tracingId: Option[Long])
}
