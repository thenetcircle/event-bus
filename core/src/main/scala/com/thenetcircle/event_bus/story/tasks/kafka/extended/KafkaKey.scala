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

package com.thenetcircle.event_bus.story.tasks.kafka.extended

import com.thenetcircle.event_bus.event.extractor.DataFormat
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.story.tasks.kafka.extended.KafkaKey._

import scala.util.control.NonFatal
import scala.util.matching.Regex

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
    new KafkaKey(KafkaKeyData(event.body.format, event.uuid))

  /** Holds real data of the key of a kafka message */
  case class KafkaKeyData(eventFormat: DataFormat, uuid: String)

  val delimiter = "|||"

  /** Serializes [[KafkaKeyData]]
    * @param data
    * @return the serialized value
    */
  def packRawData(data: KafkaKeyData): String =
    data.eventFormat.toString + delimiter + data.uuid + delimiter

  /** Deserializes rawdata to be [[KafkaKeyData]]
    * @param rawData
    * @return [[KafkaKeyData]]
    */
  def parseRawData(rawData: String): Option[KafkaKeyData] =
    try {
      if (rawData.substring(rawData.length - delimiter.length) == delimiter) {
        val parsed      = rawData.split(Regex.quote(delimiter))
        val eventFormat = DataFormat(parsed(0))
        val uuid        = if (parsed.isDefinedAt(1)) parsed(1) else ""
        Some(KafkaKeyData(eventFormat, uuid))
      } else {
        None
      }
    } catch {
      case NonFatal(_) => None
    }
}
