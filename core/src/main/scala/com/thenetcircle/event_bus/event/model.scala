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

package com.thenetcircle.event_bus.event

import java.util.Date

import com.thenetcircle.event_bus.event.extractor.DataFormat
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat

trait Event {
  def uuid: String
  def metadata: EventMetaData
  def body: EventBody
  def createdAt: Date
  def passThrough: Option[Any]
  def summary: String
  def summaryWithContent: String

  def withPassThrough[T](_passThrough: T): Event
  def getPassThrough[T]: Option[T] = passThrough.map(_.asInstanceOf[T])
  def withTopic(_topic: String): Event
  def withNoTopic(): Event
  def withName(_name: String): Event
  def withUUID(_uuid: String): Event
  def withBody(_body: EventBody): Event
  def withBody(_data: String): Event
  def hasExtra(_key: String): Boolean
  def getExtra(_key: String): Option[String]
  def withExtra(_key: String, _value: String): Event
}

object Event {
  def fromException(
      ex: Throwable,
      _body: EventBody = EventBody("", DataFormat.UNKNOWN),
      _passThrough: Option[Any] = None
  ): DefaultEventImpl =
    DefaultEventImpl(
      uuid = "ExceptionalEvent-" + java.util.UUID.randomUUID().toString,
      metadata = EventMetaData(
        name = Some("event-bus.exception.throw"),
        extra = Map(
          "class"   -> ex.getClass.getName,
          "message" -> ex.getMessage
        )
      ),
      body = _body,
      passThrough = _passThrough
    )
}

sealed trait EventStatus
object EventStatus {
  sealed trait SuccStatus extends EventStatus
  sealed trait FailStatus extends EventStatus

  case object NORMAL                                                                extends SuccStatus
  case object SKIPPING                                                              extends SuccStatus
  case object STAGED                                                                extends SuccStatus
  case class STAGING(cause: Option[Throwable] = None, taskName: String = "unknown") extends FailStatus
  case class FAILED(cause: Throwable, taskName: String = "unknown")                 extends FailStatus
}

case class EventMetaData(
    name: Option[String] = None,
    channel: Option[String] = None,
    topic: Option[String] = None,
    extra: Map[String, String] = Map.empty,
    transportMode: Option[EventTransportMode] = None
)

case class EventBody(data: String, format: DataFormat)
object EventBody {
  def apply(data: Array[Byte], format: DataFormat): EventBody =
    EventBody(new String(data, "UTF-8"), format)
}

sealed trait EventTransportMode
object EventTransportMode {
  case object SYNC_PLUS           extends EventTransportMode
  case object ASYNC               extends EventTransportMode
  case object BOTH                extends EventTransportMode
  case class OTHERS(mode: String) extends EventTransportMode

  def getFromString(mode: String): EventTransportMode = mode.toUpperCase match {
    case "SYNC_PLUS" => SYNC_PLUS
    case "ASYNC"     => ASYNC
    case "BOTH"      => BOTH
    case _           => OTHERS(mode)
  }
}
