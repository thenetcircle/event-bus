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

import akka.util.ByteString
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat

case class Event(metadata: EventMetaData, body: EventBody, passThrough: Option[Any] = None) {

  def uniqueName: String = s"${metadata.name}-${metadata.uuid}"

  def withPassThrough[T](_passThrough: T): Event = {
    if (passThrough.isDefined) {
      throw new Exception("event passthrough is defined already.")
    }
    copy(passThrough = Some(_passThrough))
  }
  def getPassThrough[T]: Option[T] = passThrough.map(_.asInstanceOf[T])

  def withChannel(_channel: String): Event = copy(metadata = metadata.withChannel(_channel))

}

object Event {
  val EXCEPTION_EVENTNAME = "event-bus-exception"
  def createEventFromException(body: ByteString, dataFormat: DataFormat, ex: Throwable): Event = {
    Event(
      metadata = EventMetaData(
        java.util.UUID.randomUUID().toString,
        EXCEPTION_EVENTNAME,
        System.currentTimeMillis()
      ),
      body = EventBody(body, dataFormat),
      Some(ex)
    )
  }
}

case class EventMetaData(uuid: String,
                         name: String,
                         published: Long,
                         provider: Option[String] = None, // who provided the event
                         actor: Option[String] = None, // who triggered the event
                         channel: Option[String] = None) {
  def withChannel(_channel: String): EventMetaData = copy(channel = Some(_channel))
}

case class EventBody(data: ByteString, format: DataFormat)
