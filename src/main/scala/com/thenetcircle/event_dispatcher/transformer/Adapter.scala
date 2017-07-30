package com.thenetcircle.event_dispatcher.transformer
import com.thenetcircle.event_dispatcher.UnExtractedEvent

trait Adapter[T]
{
  def adapt(message: T): UnExtractedEvent
  def deAdapt(event: UnExtractedEvent): T
}
