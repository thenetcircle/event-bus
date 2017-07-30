package com.thenetcircle.event_dispatcher.event

final case class EventExtracterSettings()

object EventExtracterFactory {
  def fromSettings(settings: EventExtracterSettings): EventExtracter = ???
}
