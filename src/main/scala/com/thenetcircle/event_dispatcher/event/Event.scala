package com.thenetcircle.event_dispatcher.event

final case class UnExtractedEvent(body: EventBody, context: EventContext)
final case class Event(
    body: EventBody,
    context: EventContext,
    uuid: String,
    channel: String = "default",
    provider: Option[String] = None,
    category: Option[String] = None
)
