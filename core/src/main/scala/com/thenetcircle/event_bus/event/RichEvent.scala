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

import com.thenetcircle.event_bus.interfaces.{Event, EventBody, EventMetaData}

case class RichEvent(uuid: String,
                     metadata: EventMetaData,
                     body: EventBody,
                     createdAt: Date = Date.from(Instant.now()),
                     passThrough: Option[Any] = None)
    extends Event {

  override def withPassThrough[T](_passThrough: T): Event = {
    if (passThrough.isDefined) {
      throw new Exception("event passthrough is defined already.")
    }
    copy(passThrough = Some(_passThrough))
  }

  override def withGroup(_group: String): Event =
    copy(metadata = metadata.copy(group = Some(_group)))

  override def withUUID(_uuid: String): Event = copy(uuid = _uuid)
}
