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

import scala.concurrent.Future

sealed trait EventFormat
object EventFormat {
  type DefaultFormat = DefaultFormat.type
  case object DefaultFormat extends EventFormat

  type TestFormat = TestFormat.type
  case object TestFormat extends EventFormat

  def apply(formatString: String): EventFormat = formatString match {
    case "DefaultFormat" => DefaultFormat
    case "TestFormat"    => TestFormat
  }
}

trait EventCommitter {
  def commit(): Future[Any]
}

sealed trait EventSourceType
object EventSourceType {
  case object Redis extends EventSourceType
  case object AMQP  extends EventSourceType
  case object Kafka extends EventSourceType
  case object Http  extends EventSourceType
}

object EventPriority extends Enumeration {
  type EventPriority = Value
  val Urgent = Value(6, "Urgent")
  val High   = Value(5, "High")
  val Medium = Value(4, "Medium")
  val Normal = Value(3, "Normal")
  val Low    = Value(2, "Low")
}

case class EventBody(
    data: ByteString,
    format: EventFormat
)

case class EventMetaData(
    uuid: String,
    name: String,
    timestamp: Long,
    publisher: String,
    trigger: Tuple2[String, String]
)

class EventContextValue {
  def set[T](value: T) =
  def get[T](): T = value
}

case class Event(
    metadata: EventMetaData,
    body: EventBody,
    channel: String,
    sourceType: EventSourceType,
    priority: EventPriority.EventPriority = EventPriority.Normal,
    context: Map[String, EventContextValue] = Map.empty,
    committer: Option[EventCommitter] = None
) {

  def withCommitter(commitFunction: () => Future[Any]): Event =
    copy(committer = Some(new EventCommitter {
      override def commit(): Future[Any] = commitFunction()
    }))

  def withPlusPriority(plusPriority: Int): Event =
    withPriority(EventPriority(priority.id + plusPriority))

  def withPriority(priority: EventPriority.EventPriority): Event =
    copy(priority = priority)

  def hasContext(key: String): Boolean = context.isDefinedAt(key)

  def addContext(key: String, value: Any): Event =
    copy(context = context + (key -> value))

}
