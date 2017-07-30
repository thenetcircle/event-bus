package com.thenetcircle.event_dispatcher.event

sealed trait EventExtracter {
  def extract(event: UnExtractedEvent): Event
}

class PlainEventExtracter extends EventExtracter {
  override def extract(event: UnExtractedEvent): Event = Event(event.body, event.context, "")
}

class ActivityStreamsEventExtracter extends EventExtracter {
  override def extract(event: UnExtractedEvent): Event = Event(event.body, event.context, "")
}
