package com.thenetcircle.event_bus.story

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer, Supervision}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class TaskRunningContextFactory()(implicit runningEnv: RunningEnvironment) extends StrictLogging {
  implicit val system: ActorSystem = runningEnv.getActorSystem()

  val decider: Supervision.Decider = {
    case NonFatal(ex) =>
      logger.error(s"supervision error: $ex")
      Supervision.Resume
  }

  private lazy val materializer: Materializer = ActorMaterializer(
    ActorMaterializerSettings(system)
    // .withSupervisionStrategy(decider)
  )
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
