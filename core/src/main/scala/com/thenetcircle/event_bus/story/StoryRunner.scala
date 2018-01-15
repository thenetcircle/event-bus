package com.thenetcircle.event_bus.story

import akka.actor.{Actor, ActorLogging, Props}

object StoryRunner {
  def props(story: Story)(implicit runningContext: TaskRunningContext): Props =
    Props(classOf[StoryRunner], story)
}

class StoryRunner(story: Story)(implicit runningContext: TaskRunningContext)
    extends Actor
    with ActorLogging {

  story.run()

  override def receive: Receive = Actor.emptyBehavior

}
