package com.thenetcircle.event_dispatcher.extractor

import com.thenetcircle.event_dispatcher.{Event, RawEvent}
import io.jvm.uuid.UUID

trait Extractor[Fmt] {
  def extract(rawEvent: RawEvent): Event
  def genUUID(): String = UUID.random.toString
}
