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

case class RawEvent(
    body: ByteString,
    channel: String,
    context: Map[String, Any],
    source: EventSourceType
) {
  def hasContext(key: String): Boolean = context.isDefinedAt(key)
  def addContext(key: String, value: Any): RawEvent = copy(context = context + (key -> value))
}

case class BizData(
    sessionId: Option[String] = None,
    provider: Option[String] = None,
    category: Option[String] = None,
    actorId: Option[String] = None,
    actorType: Option[String] = None
)

sealed trait EventFormat
object EventFormat {
  case class TncActivityStreams() extends EventFormat
}

trait EventCommitter {
  def commit(): Future[Any]
}

sealed trait EventSourceType
object EventSourceType {
  case object Redis extends EventSourceType
  case object AMQP extends EventSourceType
  case object Kafka extends EventSourceType
  case object Http extends EventSourceType
  case object Fallback extends EventSourceType
}

case class EventMetaData(
    uuid: String,
    name: String,
    timestamp: Long,
    publisher: String,
    trigger: Tuple2[String, String]
)

case class Event(
    metadata: EventMetaData,
    body: ByteString,
    channel: String,
    sourceType: EventSourceType,
    format: EventFormat,
    context: Map[String, Any] = Map.empty,
    committer: Option[EventCommitter] = None
) {

  def withCommitter[T](committerBuilder: => Future[T]): Event =
    copy(committer = Some(new EventCommitter {
      override def commit(): Future[T] = committerBuilder
    }))

  def hasContext(key: String): Boolean = context.isDefinedAt(key)

  def addContext(key: String, value: Any): Event = copy(context = context + (key -> value))

}
