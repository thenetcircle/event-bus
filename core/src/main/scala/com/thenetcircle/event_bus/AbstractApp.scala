package com.thenetcircle.event_bus

import akka.actor.{ActorRef, ActorSystem}
import com.thenetcircle.event_bus.ZKBasedRunner.{args, checkArg, logger}
import com.thenetcircle.event_bus.context.AppContext
import com.thenetcircle.event_bus.story.StoryRunner
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Await
import scala.concurrent.duration._

class AbstractApp {

  def initRunner(): Unit = {
    // Base components
    val config: Config = ConfigFactory.load()
    implicit val appContext: AppContext = AppContext(config)
    implicit val system: ActorSystem = ActorSystem(appContext.getAppName(), config)

    // Initialize StoryRunner
    checkArg(0, "the first argument runner-name is required")
    var runnerName: String = args(0)
    val storyRunner: ActorRef =
      system.actorOf(StoryRunner.props(runnerName), "runner-" + runnerName)

    // Setup shutdown hooks
    sys.addShutdownHook({
      logger.info("Application is shutting down...")
      Await
        .result(
          akka.pattern.gracefulStop(storyRunner, 3.seconds, StoryRunner.Shutdown()),
          3.seconds
        )
      appContext.shutdown()
      system.terminate()
      Await.result(system.whenTerminated, 6.seconds)
    })
  }

}
