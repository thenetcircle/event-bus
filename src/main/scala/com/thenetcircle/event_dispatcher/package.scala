package com.thenetcircle

import akka.util.ByteString

package object event_dispatcher {

  type EventBody       = ByteString
  type EventContext    = Map[String, Any]

  final case class UnExtractedEvent(
    body:    EventBody,
    context: EventContext,
    channel: Option[String]  = None,
    uuid:    Option[String]  = None,
    provider: Option[String] = None,
    category: Option[String] = None
  )

  final case class Event(
    uuid: String,
    provider: String,
    category: String,
    channel: String,
    body: EventBody,
    context: EventContext
  )

}