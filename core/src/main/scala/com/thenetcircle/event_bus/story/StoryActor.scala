package com.thenetcircle.event_bus.story

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.thenetcircle.event_bus.context.TaskRunningContext

import scala.concurrent.ExecutionContext

object StoryActor {
  def props(story: Story, runner: ActorRef)(implicit runningContext: TaskRunningContext): Props =
    Props(classOf[StoryActor], story, runner, runningContext)

  case object Shutdown
}

class StoryActor(story: Story, runner: ActorRef)(implicit runningContext: TaskRunningContext)
    extends Actor
    with ActorLogging {

  import StoryActor._

  val storyName: String = story.storyName

  implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

  override def preStart(): Unit = {
    log.info(s"starting the actor of story $storyName")
    val doneFuture = story.run()
    // fix the case if this actor is dead already, the Shutdown commend will send to dead-letter
    val selfPath = self.path
    doneFuture.onComplete(_ => context.actorSelection(selfPath) ! Shutdown)
  }

  override def postStop(): Unit = {
    log.warning(s"stopping the actor of story $storyName")
    story.shutdown()
  }

  override def receive: Receive = {
    case Shutdown =>
      log.debug(s"shutting down the actor of story $storyName")
      context.stop(self)
  }
}
