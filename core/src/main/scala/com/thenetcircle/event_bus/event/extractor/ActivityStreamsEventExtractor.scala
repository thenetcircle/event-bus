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

import java.text.{ParseException, SimpleDateFormat}
import java.time.Instant
import java.util.Date

import com.thenetcircle.event_bus.event._
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.thenetcircle.event_bus.interfaces.{Event, EventBody, EventMetaData}
import com.typesafe.scalalogging.StrictLogging
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

case class Activity(
    title: Option[String],
    id: Option[String],
    published: Option[String],
    verb: Option[String],
    actor: Option[ActivityObject],
    // `object`: Option[ActivityObject],
    target: Option[ActivityObject],
    provider: Option[ActivityObject],
    // content: Option[Any],
    generator: Option[ActivityObject]
)

case class ActivityObject(
    id: Option[String],
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
  implicit val activityFormat       = jsonFormat8(Activity)
}

class ActivityStreamsEventExtractor extends EventExtractor with ActivityStreamsProtocol with StrictLogging {

  override def getFormat(): DataFormat = DataFormat.ACTIVITYSTREAMS

  val patternsOfRFC3339: List[String] = List(
    "yyyy-MM-dd'T'HH:mm:ssX",
    "yyyy-MM-dd'T'HH:mm:ss.SSSX",
    "yyyy-MM-dd'T'HH:mm:ss'Z'",
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
  )

  override def extract(_data: Array[Byte], passThrough: Option[Any] = None)(
      implicit executionContext: ExecutionContext
  ): Future[Event] = Future {
    val data = new String(_data, "UTF-8")
    try {
      val activity = data.parseJson.convertTo[Activity]

      val uuid: String =
        activity.id.getOrElse(
          activity.title.getOrElse("") + "#" + java.util.UUID.randomUUID().toString
        )

      val metaData = EventMetaData(
        name = activity.title,
        verb = activity.verb,
        provider = activity.provider.map(o => o.objectType.getOrElse("")   -> o.id.getOrElse("")),
        generator = activity.generator.map(o => o.objectType.getOrElse("") -> o.id.getOrElse("")),
        actor = activity.actor.map(o => o.objectType.getOrElse("")         -> o.id.getOrElse("")),
        target = activity.target.map(o => o.objectType.getOrElse("")       -> o.id.getOrElse(""))
      )

      var createdAt: Option[Date] = None
      if (activity.published.isDefined) {
        val _published = activity.published.get
        patternsOfRFC3339.foreach(
          pt =>
            if (createdAt.isEmpty) {
              try {
                val _d = new SimpleDateFormat(pt).parse(_published)
                createdAt = Some(_d)
              } catch {
                case _: ParseException =>
              }
          }
        )
      }

      NormalEvent(
        uuid = uuid,
        metadata = metaData,
        body = EventBody(data, getFormat()),
        createdAt = createdAt.getOrElse(Date.from(Instant.now())),
        passThrough = passThrough
      )
    } catch {
      case NonFatal(ex) =>
        logger.debug(s"Parsing data $data failed with error: ${ex.getMessage}")
        throw new EventExtractingException(ex.getMessage, ex)
    }
  }
}
