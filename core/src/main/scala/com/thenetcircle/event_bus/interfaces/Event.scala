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

package com.thenetcircle.event_bus.interfaces

import java.util.Date

import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat

trait Event {
  def uuid: String
  def metadata: EventMetaData
  def body: EventBody
  def createdAt: Date
  def passThrough: Option[Any]

  def withPassThrough[T](_passThrough: T): Event
  def getPassThrough[T]: Option[T] = passThrough.map(_.asInstanceOf[T])
  def withGroup(_group: String): Event
  def withNoGroup(): Event
  def withName(_name: String): Event
  def withUUID(_uuid: String): Event
  def withBody(_body: EventBody): Event
  def withBody(_data: String): Event
  def getExtra(_key: String): Option[String]
  def withExtra(_key: String, _value: String): Event
}

case class EventMetaData(
    name: Option[String] = None,
    group: Option[String] = None,
    extra: Map[String, String] = Map.empty
)

case class EventBody(data: String, format: DataFormat)
object EventBody {
  def apply(data: Array[Byte], format: DataFormat): EventBody =
    EventBody(new String(data, "UTF-8"), format)
}
