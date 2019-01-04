package com.thenetcircle.event_bus
import akka.NotUsed
import com.thenetcircle.event_bus.event.{Event, EventStatus}

package object story {

  type Payload  = (EventStatus, Event)
  type StoryMat = NotUsed

}
