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

import java.text.SimpleDateFormat

import akka.util.ByteString
import com.thenetcircle.event_bus.{ EventBody, EventFormat, EventMetaData }
import spray.json._

import scala.concurrent.{ ExecutionContext, Future }

/*case class ActivityObject(
    // attachments: List[ActivityObject],
    // author: ActivityObject,
    content: Option[String],
    displayName: Option[String],
    downstreamDuplicates: Option[Array[String]],
    id: Option[String],
    objectType: Option[String],
    published: Option[String],
    summary: Option[String],
    updated: Option[String],
    upstreamDuplicates: Option[Array[String]],
    url: Option[String]
)

case class Activity(
    actor: ActivityObject,
    content: Option[String],
    generator: Option[ActivityObject],
    id: Option[String],
    `object`: Option[ActivityObject],
    published: Option[String],
    provider: Option[ActivityObject],
    target: Option[ActivityObject],
    title: Option[String],
    updated: Option[String],
    url: Option[String],
    verb: Option[String]
)*/

case class TNCContext(
    id: Option[String],
    objectType: Option[String]
)

case class TNCObject(
    id: Option[String],
    objectType: Option[String],
    attachments: Option[List[TNCContext]]
)

case class TNCActivity(
    verb: String,
    actor: TNCObject,
    id: Option[String],
    published: Option[String],
    provider: Option[TNCObject]
)

object TNCActivityStreamsProtocol extends DefaultJsonProtocol {
  implicit val tncContextFormat = jsonFormat2(TNCContext)
  implicit val tncObjectFormat = jsonFormat3(TNCObject)
  implicit val tncActivityFormat = jsonFormat5(TNCActivity)
}

abstract class TNCActivityStreamsExtractor {
  import TNCActivityStreamsProtocol._

  def extract(data: ByteString)(implicit executor: ExecutionContext): Future[ExtractedData] = Future {

    val jsonAst = data.utf8String.parseJson
    val tncActivity = jsonAst.convertTo[TNCActivity]

    val uuid = tncActivity.id.getOrElse(Extractor.genUUID())
    val name = tncActivity.verb
    val timestamp = tncActivity.published match {
      case Some(datetime: String) =>
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(datetime).getTime
      case None => System.currentTimeMillis()
    }
    val publisher = tncActivity.provider match {
      case Some(o) => o.id.getOrElse("")
      case None => ""
    }
    val actor = tncActivity.actor

    ExtractedData(
      body = getEventBody(data),
      metadata = EventMetaData(uuid, name, timestamp, publisher, actor.id.get -> actor.objectType.get)
    )

  }

  def getEventBody(data: ByteString): EventBody[EventFormat]
}
