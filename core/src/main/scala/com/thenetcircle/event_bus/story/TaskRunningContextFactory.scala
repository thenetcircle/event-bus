package com.thenetcircle.event_bus.story

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.ExecutionContext

class TaskRunningContextFactory()(implicit environment: RunningEnvironment) {

  implicit private val system: ActorSystem = environment.getActorSystem()
  private lazy val materializer: Materializer = ActorMaterializer()
  // TODO: change this
  private lazy val executor: ExecutionContext = ExecutionContext.global

  def createTaskRunningContext(): TaskRunningContext = {
    new TaskRunningContext(environment, system, materializer, executor)
  }

}

object TaskRunningContextFactory {
  def apply()(implicit environment: RunningEnvironment): TaskRunningContextFactory =
    new TaskRunningContextFactory()
}
