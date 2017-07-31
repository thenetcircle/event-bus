package com.thenetcircle

package object event_dispatcher {

  final case class UnExtractedEvent(
      body: String,
      context: Map[String, Any],
      channel: Option[String]
  )

  final case class Event(
      uuid: String,
      body: String,
      context: Map[String, Any],
      provider: Option[String],
      category: Option[String],
      channel: Option[String]
  )

}
