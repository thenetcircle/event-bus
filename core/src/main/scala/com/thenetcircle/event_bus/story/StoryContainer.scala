package com.thenetcircle.event_bus.story

import akka.actor.{Actor, ActorLogging}

object StoryContainer {}

class StoryContainer extends Actor with ActorLogging {

  override def receive: Receive = {}

}
