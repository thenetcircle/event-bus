package com.thenetcircle.event_bus.plots.kafka

import com.thenetcircle.event_bus.event.EventMetaData

class TopicResolver {}

object TopicResolver {

  val defaultChannel = "event-default"

  def getChannel(metadata: EventMetaData): String = {

    // TODO add resolve logic
    defaultChannel

  }

}
