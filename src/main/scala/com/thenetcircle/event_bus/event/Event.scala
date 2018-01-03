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
import com.thenetcircle.event_bus.event.EventStatus.EventStatus
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat

import scala.concurrent.Future

case class Event(metadata: EventMetaData,
                 body: EventBody,
                 context: Map[String, Any] = Map.empty,
                 committer: Option[EventCommitter] = None,
                 status: EventStatus = EventStatus.PROCESSING,
                 version: Option[String] = None) {

  def withCommitter(commitFunction: () => Future[Any]): Event =
    copy(committer = Some(new EventCommitter {
      override def commit(): Future[Any] = commitFunction()
    }))

  def hasContext(key: String): Boolean = context.isDefinedAt(key)
  def addContext(key: String, value: Any): Event =
    copy(context = context + (key -> value))

  def isFailed: Boolean                       = status == EventStatus.FAILED
  def withStatus(_status: EventStatus): Event = copy(status = _status)

  def withNewVersion(_version: String): Event = copy(version = Some(_version))

  def uniqueName: String = ""

}

case class EventMetaData(uuid: String,
                         name: String,
                         published: Long,
                         provider: Option[String], // who provided the event
                         actor: Option[String], // who triggered the event
                         channel: Option[String] = None) {
  def withChannel(_channel: String): EventMetaData = copy(channel = Some(_channel))
}

case class EventBody(data: ByteString, format: DataFormat)

trait EventCommitter {
  def commit(): Future[Any]
}

object EventStatus extends Enumeration {
  type EventStatus = Value

  val PROCESSING = Value(1, "PROCESSING")
  val FAILED     = Value(2, "FAILED")
}
