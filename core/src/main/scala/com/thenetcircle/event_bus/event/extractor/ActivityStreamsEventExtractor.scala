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

package com.thenetcircle.event_bus.event.extractor

import java.text.SimpleDateFormat

import akka.util.ByteString
import com.thenetcircle.event_bus.event._
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.typesafe.scalalogging.StrictLogging
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

case class Activity(title: String,
                    id: Option[String],
                    published: Option[String],
                    verb: Option[String],
                    actor: Option[ActivityObject],
                    // `object`: Option[ActivityObject],
                    // target: Option[ActivityObject],
                    provider: Option[ActivityObject],
                    // content: Option[Any],
                    // generator: Option[ActivityObject]
)

case class ActivityObject(id: Option[String],
                          objectType: Option[String],
                          // attachments: Option[List[ActivityObject]],
                          // content: Option[Any],
                          // summary: Option[Any],
                          // downstreamDuplicates: Option[List[String]],
                          // upstreamDuplicates: Option[List[String]],
                          // author: Option[ActivityObject]
)

trait ActivityStreamsProtocol extends DefaultJsonProtocol {
  implicit val activityObjectFormat = jsonFormat2(ActivityObject)
  implicit val activityFormat = jsonFormat6(Activity)
}

class ActivityStreamsEventExtractor
    extends EventExtractor
    with ActivityStreamsProtocol
    with StrictLogging {

  override def getFormat(): DataFormat = DataFormat.ACTIVITYSTREAMS

  override def extract(
      data: ByteString
  )(implicit executionContext: ExecutionContext): Future[Event] = Future {
    try {
      val jsonAst = data.utf8String.parseJson
      val activity = jsonAst.convertTo[Activity]

      val uuid: String = activity.id.getOrElse(java.util.UUID.randomUUID().toString)
      val name: String = activity.title
      val published: Long = activity.published match {
        case Some(p) =>
          new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
            .parse(p)
            .getTime

        case None =>
          System.currentTimeMillis()
      }
      val provider = activity.provider.flatMap(_.id)
      val actor = activity.actor.flatMap(actor => Some(actor.id.getOrElse("")))

      Event(
        metadata = EventMetaData(uuid, name, published, provider, actor), //
        body = EventBody(data, getFormat())
      )
    } catch {
      case ex: Throwable =>
        logger.warn(s"Parsing data ${data.utf8String} failed with error: ${ex.getMessage}")
        throw ex
    }
  }
}
