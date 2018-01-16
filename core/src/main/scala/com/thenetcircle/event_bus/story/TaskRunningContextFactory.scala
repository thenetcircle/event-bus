package com.thenetcircle.event_bus.story

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.ExecutionContext

class TaskRunningContextFactory()(implicit runningEnv: RunningEnvironment) {
  implicit val system: ActorSystem = runningEnv.getActorSystem()

  private lazy val materializer: Materializer = ActorMaterializer()
  // TODO: use another execution context for computing
  private lazy val executionContext: ExecutionContext = ExecutionContext.global

  def createNewRunningContext(storyRunner: ActorRef): TaskRunningContext = {
    new TaskRunningContext(runningEnv, system, materializer, executionContext, storyRunner)
  }
}

object TaskRunningContextFactory {
  def apply()(implicit runningEnv: RunningEnvironment): TaskRunningContextFactory =
    new TaskRunningContextFactory()
}
