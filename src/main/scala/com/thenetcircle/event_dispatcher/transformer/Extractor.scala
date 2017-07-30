package com.thenetcircle.event_dispatcher.transformer
import com.thenetcircle.event_dispatcher.{Event, UnExtractedEvent}

trait Extractor
{
  def extract(unExtractedEvent: UnExtractedEvent): Event
  def deExtract(event: Event): UnExtractedEvent
}
