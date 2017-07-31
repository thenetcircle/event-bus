package com.thenetcircle.event_dispatcher.transformer
import com.thenetcircle.event_dispatcher.{Event, RawEvent}
import io.jvm.uuid._

trait Extractor {
  def extract(rawEvent: RawEvent): Event
  def genUUID(): String = UUID.random.toString
}
