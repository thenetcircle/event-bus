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
import com.thenetcircle.event_bus.event.{Event, EventBody, EventMetaData, EventTransportMode}
import spray.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.util.parsing.json.JSON

case class Activity(
    title: Option[String],
    id: Option[String],
    published: Option[String],
    verb: Option[String],
    actor: Option[GeneralObject],
    `object`: Option[GeneralObject],
    target: Option[GeneralObject],
    provider: Option[GeneralObject],
    // content: Option[Any],
    generator: Option[GeneratorObject]
)

sealed trait ActivityObject {
  def id: Option[String]
  def objectType: Option[String]
}

case class GeneralObject(
    id: Option[String],
    objectType: Option[String]
    // url: Option[String]
    // attachments: Option[List[ActivityObject]],
    // content: Option[Any],
    // summary: Option[Any],
    // downstreamDuplicates: Option[List[String]],
    // upstreamDuplicates: Option[List[String]],
    // author: Option[ActivityObject]
) extends ActivityObject

case class GeneratorObject(
    id: Option[String],
    objectType: Option[String],
    content: Option[String]
) extends ActivityObject

trait ActivityStreamsProtocol extends DefaultJsonProtocol {
  implicit val generalObjectFormat   = jsonFormat2(GeneralObject)
  implicit val generatorObjectFormat = jsonFormat3(GeneratorObject)
  implicit val activityFormat        = jsonFormat9(Activity)
}

class ActivityStreamsEventExtractor extends EventExtractor with ActivityStreamsProtocol {

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

      var extra: Map[String, String] = Map.empty
      val setExtraFromActivityObject =
        (objOption: Option[ActivityObject], prefix: String) => {
          objOption.foreach(o => {
            o.id.foreach(s => extra = extra + (s"${prefix}Id"           -> s))
            o.objectType.foreach(s => extra = extra + (s"${prefix}Type" -> s))
          })
        }

      // TODO performance test for parse how many fields
      activity.verb.foreach(s => extra = extra + ("verb" -> s))
      setExtraFromActivityObject(activity.provider, "provider")
      setExtraFromActivityObject(activity.actor, "actor")
      setExtraFromActivityObject(activity.target, "target")
      setExtraFromActivityObject(activity.`object`, "object")
      setExtraFromActivityObject(activity.generator, "generator")

      val generatorContent: Option[Map[String, Any]] = activity.generator
        .filter(_.id.contains("tnc-event-dispatcher"))
        .flatMap(_.content)
        .flatMap(JSON.parseFull)
        .map { case m: Map[String, Any] => m }
      val channel: Option[String] = generatorContent.flatMap(
        _map => _map.get("channel").map(_.asInstanceOf[String])
      )
      val transportMode: Option[EventTransportMode] = generatorContent.flatMap(
        _map => _map.get("mode").map(_.asInstanceOf[String]).map(EventTransportMode.getFromString)
      )

      val metaData = EventMetaData(
        name = activity.title,
        channel = channel,
        topic = None,
        extra = extra,
        transportMode = transportMode
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

      var uuid: String = ""
      var body: String = data
      if (activity.id.isDefined) {
        uuid = activity.id.get
      } else {
        uuid = "EB-" + activity.title
          .map(_ + "-")
          .getOrElse("") + java.util.UUID.randomUUID().toString
        body.replaceFirst(Regex.quote("{"), s"""{"id": "$uuid",""")
      }

      DefaultEventImpl(
        uuid = uuid,
        metadata = metaData,
        body = EventBody(body, getFormat()),
        createdAt = createdAt.getOrElse(Date.from(Instant.now())),
        passThrough = passThrough
      )
    } catch {
      case NonFatal(ex) =>
        throw new EventExtractingException(ex.getMessage, ex)
    }
  }
}
