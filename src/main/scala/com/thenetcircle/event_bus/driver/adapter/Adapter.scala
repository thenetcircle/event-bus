package com.thenetcircle.event_bus.driver.adapter

import com.thenetcircle.event_bus.RawEvent

trait SourceAdapter[In] {
  def fit(message: In): RawEvent
}

trait SinkAdapter[Out] {
  def unfit(event: RawEvent): Out
}
