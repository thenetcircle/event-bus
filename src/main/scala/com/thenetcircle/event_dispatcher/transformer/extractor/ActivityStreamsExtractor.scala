package com.thenetcircle.event_dispatcher.transformer.extractor

import java.text.SimpleDateFormat

import com.thenetcircle.event_dispatcher.{EventFmt, BizData, Event, RawEvent}
import com.thenetcircle.event_dispatcher.transformer.Extractor
import spray.json._

case class ActivityObject(
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

sealed trait ActivityTrait

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
) extends ActivityTrait

case class ThinActivity(
    actor: ActivityObject,
    id: Option[String],
    published: Option[String],
    provider: Option[ActivityObject],
    verb: Option[String]
) extends ActivityTrait

case class FatActivity(
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
    verb: Option[String],
    version: Option[String],
    context: Option[Map[String, JsValue]],
    extra: Option[Map[String, JsValue]]
) extends ActivityTrait

object ActivityStreamsProtocol extends DefaultJsonProtocol {
  implicit val activityObjectFormat = jsonFormat10(ActivityObject)
  implicit val activityFormat = jsonFormat12(Activity)
  implicit val thinFormat = jsonFormat5(ThinActivity)
  implicit val fatActivityFormat = jsonFormat15(FatActivity)
}

class ActivityStreamsExtractor extends Extractor {

  import ActivityStreamsProtocol._

  override def extract(rawEvent: RawEvent): Event = {
    val jsonAst = rawEvent.body.utf8String.parseJson
    val activity = jsonAst.convertTo[ThinActivity]

    val provider = activity.provider match {
      case Some(p: ActivityObject) => p.id
      case _                       => None
    }
    val category = activity.verb
    val actor = activity.actor

    val timestamp = activity.published match {
      case Some(datetime: String) =>
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(datetime).getTime
      case None => System.currentTimeMillis()
    }
    val bizData = BizData(
      sessionId = activity.id,
      provider = provider,
      category = category,
      actorId = actor.id,
      actorType = actor.objectType
    )

    Event(genUUID(), timestamp, rawEvent, bizData, EventFmt.ActivityStreams)
  }

}
