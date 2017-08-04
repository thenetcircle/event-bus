package com.thenetcircle.event_dispatcher.extractor

import com.thenetcircle.event_dispatcher.{ BizData, Event, EventFmt, RawEvent }

class PlainExtractor extends Extractor[EventFmt.Plain] {

  override def extract(rawEvent: RawEvent): Event =
    Event(genUUID(), System.currentTimeMillis(), rawEvent, BizData(), EventFmt.Plain())

}
