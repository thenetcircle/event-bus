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
import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader

import scala.concurrent.Future

sealed trait EventFormat
object EventFormat {
  type DefaultFormat = DefaultFormat.type
  object DefaultFormat extends EventFormat {
    override def toString: String = "default"
  }

  type TestFormat = TestFormat.type
  object TestFormat extends EventFormat {
    override def toString: String = "test"
  }

  def apply(formatString: String): EventFormat =
    formatString.toLowerCase match {
      case "default" => DefaultFormat
      case "test"    => TestFormat
      case _         => DefaultFormat
    }

  implicit val eventFormatReader: ValueReader[EventFormat] =
    new ValueReader[EventFormat] {
      override def read(config: Config, path: String) =
        EventFormat(config.getString(path))
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

case class EventBody(
    data: ByteString,
    format: EventFormat
)

case class EventMetaData(
    uuid: String,
    name: String,
    publishTime: Long,
    publisher: String,
    trigger: Tuple2[String, String] // triggerType: String -> triggerId: String
)

case class Event(
    metadata: EventMetaData,
    body: EventBody,
    channel: String,
    sourceType: EventSourceType,
    tracingId: Long,
    context: Map[String, Any] = Map.empty,
    committer: Option[EventCommitter] = None
) {

  def withCommitter(commitFunction: () => Future[Any]): Event =
    copy(committer = Some(new EventCommitter {
      override def commit(): Future[Any] = commitFunction()
    }))

  def hasContext(key: String): Boolean = context.isDefinedAt(key)

  def addContext(key: String, value: Any): Event =
    copy(context = context + (key -> value))

}
