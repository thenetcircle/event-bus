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

case class DefaultEventImpl(
    uuid: String,
    metadata: EventMetaData,
    body: EventBody,
    status: EventStatus = EventStatus.NORM,
    createdAt: Date = Date.from(Instant.now()),
    passThrough: Option[Any] = None
) extends Event {

  override def withPassThrough[T](_passThrough: T): DefaultEventImpl = {
    if (passThrough.isDefined) {
      throw new Exception("event passthrough is defined already.")
    }
    copy(passThrough = Some(_passThrough))
  }

  override def withTopic(_topic: String): DefaultEventImpl =
    copy(metadata = metadata.copy(topic = Some(_topic)))
  override def withNoTopic(): DefaultEventImpl =
    copy(metadata = metadata.copy(topic = None))

  override def withName(_name: String): DefaultEventImpl =
    copy(metadata = metadata.copy(name = Some(_name)))

  override def withUUID(_uuid: String): DefaultEventImpl = copy(uuid = _uuid)

  override def withBody(_body: EventBody): DefaultEventImpl = copy(body = _body)
  override def withBody(_data: String): DefaultEventImpl =
    copy(body = body.copy(data = _data))

  override def hasExtra(_key: String): Boolean        = getExtra(_key).isDefined
  override def getExtra(_key: String): Option[String] = metadata.extra.get(_key)
  override def withExtra(_key: String, _value: String): Event =
    copy(metadata = metadata.copy(extra = metadata.extra + (_key -> _value)))

  override def withStatus(_status: EventStatus): DefaultEventImpl =
    copy(status = _status)
}
