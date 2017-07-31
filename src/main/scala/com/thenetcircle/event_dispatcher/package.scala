package com.thenetcircle

import akka.util.ByteString

package object event_dispatcher {

  final case class UnExtractedEvent(
      body: ByteString,
      context: Map[String, Any],
      channel: Option[String]
  )

  final case class Event(
      uuid: String,
      body: ByteString,
      context: Map[String, Any],
      channel: Option[String],
      provider: Option[String],
      category: Option[String]
  )

}
