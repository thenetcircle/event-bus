package com.thenetcircle.event_dispatcher.transformer.extractor

import com.thenetcircle.event_dispatcher.{Event, UnExtractedEvent}
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

  override def extract(unExtractedEvent: UnExtractedEvent): Event = {
    val jsonAst = unExtractedEvent.body.utf8String.parseJson
    val activity = jsonAst.convertTo[Activity]

    val provider = activity.provider match {
      case Some(p: ActivityObject) => p.id
      case _                       => None
    }
    val category = activity.verb

    Event(
      genUUID(),
      unExtractedEvent.body,
      unExtractedEvent.context,
      unExtractedEvent.channel,
      provider,
      category
    )
  }

}
