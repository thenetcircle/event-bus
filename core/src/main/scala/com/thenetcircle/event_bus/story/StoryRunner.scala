package com.thenetcircle.event_bus.story

import akka.actor.{Actor, ActorLogging, Props}

object StoryRunner {
  case object Done
  case object Stop

  def props(runningContextFactory: TaskRunningContextFactory, story: Story): Props =
    Props(new StoryRunner(runningContextFactory, story))
}

class StoryRunner(runningContextFactory: TaskRunningContextFactory, story: Story)
    extends Actor
    with ActorLogging {

  import StoryRunner._

  implicit val runningContext: TaskRunningContext =
    runningContextFactory.createNewRunningContext(self)

  val (killSwitch, doneFuture) = story.run()
  doneFuture.onComplete(_ => self ! Done)(runningContext.getExecutionContext())

  override def receive: Receive = {
    case Stop =>
      log.warning(s"the story ${story.storyName} is shuting down")
      killSwitch.shutdown()
    case Done =>
      log.warning(s"the story ${story.storyName} is completed")
      context.stop(self)
  }
}
