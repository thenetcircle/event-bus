package com.thenetcircle.event_bus.story

import akka.actor.{Actor, ActorLogging, Props}

object StoryContainer {

  def props(story: Story): Props = Props(classOf[StoryContainer], story)

}

class StoryContainer(story: Story) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    super.preStart()
  }

  override def receive: Receive = Actor.emptyBehavior

}
