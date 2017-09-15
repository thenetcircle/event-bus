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
import com.github.levkhomich.akka.tracing.TracingSupport
import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader

import scala.concurrent.Future

sealed trait EventFormat
object EventFormat {
  type DefaultFormat = DefaultFormat.type
  case object DefaultFormat extends EventFormat

  type TestFormat = TestFormat.type
  case object TestFormat extends EventFormat

  def apply(formatString: String): EventFormat =
    formatString.toUpperCase match {
      case "DEFAULT" => DefaultFormat
      case "TEST"    => TestFormat
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
    timestamp: Long,
    publisher: String,
    /** format: triggerType: String -> triggerId: String */
    trigger: Tuple2[String, String]
)

case class Event(
    metadata: EventMetaData,
    body: EventBody,
    channel: String,
    sourceType: EventSourceType,
    context: Map[String, Any] = Map.empty,
    committer: Option[EventCommitter] = None
) extends TracingSupport {

  override def spanName: String = s"${metadata.name}"

  def withCommitter(commitFunction: () => Future[Any]): Event =
    copy(committer = Some(new EventCommitter {
      override def commit(): Future[Any] = commitFunction()
    }))

  def hasContext(key: String): Boolean = context.isDefinedAt(key)

  def addContext(key: String, value: Any): Event =
    copy(context = context + (key -> value))

}
