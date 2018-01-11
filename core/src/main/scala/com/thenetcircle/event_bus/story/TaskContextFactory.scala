package com.thenetcircle.event_bus.story

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.ExecutionContext

class TaskContextFactory()(implicit environment: ExecutionEnvironment) {

  implicit private val system: ActorSystem = environment.getActorSystem()
  private lazy val materializer: Materializer = ActorMaterializer()
  // TODO: change this
  private lazy val executor: ExecutionContext = ExecutionContext.global

  def newTaskExecutingContext(): TaskContext = {
    new TaskContext(environment, system, materializer, executor)
  }

}

object TaskContextFactory {
  def apply()(implicit environment: ExecutionEnvironment): TaskContextFactory =
    new TaskContextFactory()
}
