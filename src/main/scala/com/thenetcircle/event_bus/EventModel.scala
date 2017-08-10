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

sealed trait EventFmt
object EventFmt {
  case class Plain() extends EventFmt
  case class Json() extends EventFmt
  case class ActivityStreams() extends EventFmt
}

sealed trait EventSource
object EventSource {
  object Redis extends EventSource
  object AMQP extends EventSource
  object Kafka extends EventSource
  object Http extends EventSource
}

case class RawEvent(
    body: ByteString,
    channel: String,
    context: Map[String, Any],
    source: EventSource
) {
  def hasContext(key: String): Boolean = context.isDefinedAt(key)
  def addContext(key: String, value: Any): RawEvent = copy(context = context + (key -> value))
}

trait EventCommitter[+A] {
  def commit(): Future[A]
}

/**
 * deliveredTimes?
 */
case class Event(
    uuid: String,
    timestamp: Long,
    rawEvent: RawEvent,
    bizData: BizData,
    format: EventFmt,
    committer: Option[EventCommitter[_]] = None
) {
  def withCommitter[A](committerBuilder: => Future[A]): Event =
    copy(committer = Some(new EventCommitter[A] {
      override def commit(): Future[A] = committerBuilder
    }))
}

case class BizData(
    sessionId: Option[String] = None,
    provider: Option[String] = None,
    category: Option[String] = None,
    actorId: Option[String] = None,
    actorType: Option[String] = None
)
