package com.thenetcircle.event_dispatcher.extractor

import com.thenetcircle.event_dispatcher.{ Event, EventFmt, RawEvent }
import io.jvm.uuid.UUID

trait Extractor[Fmt <: EventFmt] {
  def extract(rawEvent: RawEvent): Event
  def genUUID(): String = UUID.random.toString
}
