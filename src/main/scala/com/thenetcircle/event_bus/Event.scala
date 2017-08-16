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

package com.thenetcircle.event_bus

import akka.util.ByteString

sealed trait EventFormat
object EventFormat {
  type DefaultFormat = DefaultFormat.type
  object DefaultFormat extends EventFormat
}

trait EventCommitter {
  def commit(): Unit
}

sealed trait EventSourceType
object EventSourceType {
  case object Redis extends EventSourceType
  case object AMQP extends EventSourceType
  case object Kafka extends EventSourceType
  case object Http extends EventSourceType
  case object Fallback extends EventSourceType
}

sealed trait EventPriority
object EventPriority {
  case object High extends EventPriority
  case object Normal extends EventPriority
  case object Low extends EventPriority
}

case class EventBody[+Fmt](
    data: ByteString,
    format: Fmt
)

case class EventMetaData(
    uuid: String,
    name: String,
    timestamp: Long,
    publisher: String,
    trigger: Tuple2[String, String]
)

case class Event(
    metadata: EventMetaData,
    body: EventBody[EventFormat],
    channel: String,
    sourceType: EventSourceType,
    priority: EventPriority = EventPriority.Normal,
    context: Map[String, Any] = Map.empty,
    committer: Option[EventCommitter] = None
) {

  def withCommitter(commitFunction: () => Unit): Event =
    copy(committer = Some(new EventCommitter {
      override def commit(): Unit = commitFunction()
    }))

  def hasContext(key: String): Boolean = context.isDefinedAt(key)

  def addContext(key: String, value: Any): Event = copy(context = context + (key -> value))

}
