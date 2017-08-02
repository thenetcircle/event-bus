package com.thenetcircle.event_dispatcher.extractor
import com.thenetcircle.event_dispatcher.{ Event, RawEvent }

object DeExtractor {
  def apply(event: Event): RawEvent = event.rawEvent
}
