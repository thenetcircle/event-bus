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

import java.time.Instant
import java.util.Date

import com.thenetcircle.event_bus.event.extractor.DataFormat

case class EventImpl(
    uuid: String,
    metadata: EventMetaData,
    body: EventBody,
    createdAt: Date = Date.from(Instant.now()),
    passThrough: Option[Any] = None
) extends Event {

  override def withPassThrough[T](_passThrough: T): EventImpl = {
    if (passThrough.isDefined) {
      throw new Exception("event passthrough is defined already.")
    }
    copy(passThrough = Some(_passThrough))
  }

  override def withTopic(_topic: String): EventImpl =
    copy(metadata = metadata.copy(topic = Some(_topic)))
  override def withNoTopic(): EventImpl =
    copy(metadata = metadata.copy(topic = None))

  override def withName(_name: String): EventImpl =
    copy(metadata = metadata.copy(name = Some(_name)))

  override def withUUID(_uuid: String): EventImpl = copy(uuid = _uuid)

  override def withBody(_body: EventBody): EventImpl = copy(body = _body)
  override def withBody(_data: String): EventImpl    = copy(body = body.copy(data = _data))

  override def hasExtra(_key: String): Boolean        = getExtra(_key).isDefined
  override def getExtra(_key: String): Option[String] = metadata.extra.get(_key)
  override def withExtra(_key: String, _value: String): Event =
    copy(metadata = metadata.copy(extra = metadata.extra + (_key -> _value)))
}

object EventImpl {
  def createFromFailure(
      ex: Throwable,
      _body: EventBody = EventBody("", DataFormat.UNKNOWN),
      _passThrough: Option[Any] = None
  ): EventImpl =
    EventImpl(
      uuid = "FailureEvent-" + java.util.UUID.randomUUID().toString,
      metadata = EventMetaData(
        name = Some("event-bus.failure.trigger"),
        extra = Map(
          "class"   -> ex.getClass.getName,
          "message" -> ex.getMessage
        )
      ),
      body = _body,
      passThrough = _passThrough
    )
}
