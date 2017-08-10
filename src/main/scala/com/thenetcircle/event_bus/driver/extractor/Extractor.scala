package com.thenetcircle.event_bus.driver.extractor

import com.thenetcircle.event_bus.{ Event, EventFmt, RawEvent }
import io.jvm.uuid.UUID

trait Extractor[Fmt <: EventFmt] {
  def extract(rawEvent: RawEvent): Event
  def genUUID(): String = UUID.random.toString
}

object Extractor {

  def deExtract(event: Event): RawEvent = event.rawEvent

  implicit val plainExtractor: Extractor[EventFmt.Plain] = new PlainExtractor

  implicit val defaultActivityStreamsExtractor: Extractor[EventFmt.ActivityStreams] = new ActivityStreamsExtractor

}
