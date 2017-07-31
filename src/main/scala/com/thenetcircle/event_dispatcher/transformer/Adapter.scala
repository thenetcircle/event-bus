package com.thenetcircle.event_dispatcher.transformer
import com.thenetcircle.event_dispatcher.UnExtractedEvent

trait Adapter[In, Out] {
  def adapt(message: In): UnExtractedEvent
  def deAdapt(event: UnExtractedEvent): Out
}
