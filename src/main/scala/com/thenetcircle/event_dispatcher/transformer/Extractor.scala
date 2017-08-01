package com.thenetcircle.event_dispatcher.transformer

import com.thenetcircle.event_dispatcher.{RawEvent, Event}
import io.jvm.uuid.UUID

trait Extractor {
  def extract(rawEvent: RawEvent): Event
  def genUUID(): String = UUID.random.toString
}
