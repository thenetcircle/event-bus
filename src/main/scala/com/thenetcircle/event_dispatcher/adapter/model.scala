package com.thenetcircle.event_dispatcher.adapter

import com.thenetcircle.event_dispatcher.EventFmt

trait SourceSettings {
  def dataFormat: EventFmt
}
trait SinkSettings {}
