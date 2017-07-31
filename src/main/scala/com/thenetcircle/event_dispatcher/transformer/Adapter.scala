package com.thenetcircle.event_dispatcher.transformer
import com.thenetcircle.event_dispatcher.RawEvent

trait Adapter[In, Out] {
  def adapt(message: In): RawEvent
  def deAdapt(event: RawEvent): Out
}
