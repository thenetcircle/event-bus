package com.thenetcircle.event_bus
import com.thenetcircle.event_bus.event.extractor.EventMetaData

class ChannelResolver {}

object ChannelResolver {

  val defaultChannel = "event-default"

  def getChannel(metadata: EventMetaData): String = {

    // TODO add resolve logic
    defaultChannel

  }

}
