package com.thenetcircle.event_bus
import com.thenetcircle.event_bus.event_extractor.EventMetaData

class ChannelResolver {}

object ChannelResolver {

  val defaultChannel = "default"

  def getChannel(metadata: EventMetaData): String = {

    // TODO add resolve logic
    defaultChannel

  }

}
