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

package com.thenetcircle.event_bus.extractor

import akka.util.ByteString
import scala.concurrent.Future

/** The extracted data from [[EventExtractor]]
  *
  * @param body
  * @param metadata
  * @param channel
  */
case class ExtractedData(body: EventBody, metadata: EventMetaData, channel: Option[String] = None) {
  def withChannel(channel: String): ExtractedData =
    copy(channel = Some(channel))
}

case class EventMetaData(uuid: String,
                         name: String,
                         published: Long,
                         provider: Option[String], // who provided the event
                         actor: Option[EventActor] // who triggered the event
)

case class EventBody(data: ByteString, format: EventFormat)

trait EventCommitter {
  def commit(): Future[Any]
}

case class EventActor(id: String, objectType: String)

sealed trait EventSourceType
object EventSourceType {
  case object Redis extends EventSourceType
  case object AMQP  extends EventSourceType
  case object Kafka extends EventSourceType
  case object Http  extends EventSourceType
}
