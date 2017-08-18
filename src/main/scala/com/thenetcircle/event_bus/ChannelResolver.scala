package com.thenetcircle.event_bus

class ChannelResolver {}

object ChannelResolver {

  val defaultChannel = "default"

  def getChannel(metadata: EventMetaData): String = {

    // TODO add resolve logic
    defaultChannel

  }

}
