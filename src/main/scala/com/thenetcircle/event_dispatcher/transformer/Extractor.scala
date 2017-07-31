package com.thenetcircle.event_dispatcher.transformer
import com.thenetcircle.event_dispatcher.{Event, UnExtractedEvent}
import io.jvm.uuid._

trait Extractor {
  def extract(unExtractedEvent: UnExtractedEvent): Event
  def genUUID(): String = UUID.random.toString
}
