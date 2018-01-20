package com.thenetcircle.event_bus.story

import akka.actor.{Actor, ActorLogging, Props}
import com.thenetcircle.event_bus.context.{TaskRunningContext, TaskRunningContextFactory}

object StoryWrapper {
  case object Done
  case object Stop

  def props(runningContextFactory: TaskRunningContextFactory, story: Story): Props =
    Props(new StoryWrapper(runningContextFactory, story))
}

class StoryWrapper(runningContextFactory: TaskRunningContextFactory, story: Story)
    extends Actor
    with ActorLogging {

  import StoryWrapper._

  implicit val runningContext: TaskRunningContext =
    runningContextFactory.createNewRunningContext(self, story.settings)

  val (killSwitch, doneFuture) = story.run()
  doneFuture.onComplete(_ => self ! Done)(runningContext.getExecutionContext())

  val storyName = story.storyName

  override def postStop() = {
    log.info(s"Stopping story $storyName")
    killSwitch.shutdown()
  }

  override def receive: Receive = {
    case Stop =>
      log.warning(s"the story $storyName is shuting down")
      killSwitch.shutdown()
    case Done =>
      log.warning(s"the story $storyName is completed")
      context.stop(self)
  }
}
