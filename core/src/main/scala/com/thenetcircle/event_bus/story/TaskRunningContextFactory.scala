package com.thenetcircle.event_bus.story

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.ExecutionContext

class TaskRunningContextFactory()(implicit environment: RunningEnvironment) {

  implicit val system: ActorSystem = environment.getActorSystem()

  private lazy val materializer: Materializer = ActorMaterializer()

  // TODO: use another execution context for computing
  private lazy val executionContext: ExecutionContext = ExecutionContext.global

  def createTaskRunningContext(): TaskRunningContext = {
    new TaskRunningContext(environment, system, materializer, executionContext)
  }

}

object TaskRunningContextFactory {
  def apply()(implicit environment: RunningEnvironment): TaskRunningContextFactory =
    new TaskRunningContextFactory()
}
