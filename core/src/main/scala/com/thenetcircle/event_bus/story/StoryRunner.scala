/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Beineng Ma <baineng.ma@gmail.com>
 */

package com.thenetcircle.event_bus.story

import akka.actor.{
  Actor,
  ActorLogging,
  ActorRef,
  ActorSystem,
  OneForOneStrategy,
  Props,
  SupervisorStrategy,
  Terminated,
  Timers
}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer, Supervision}
import com.thenetcircle.event_bus.AppContext

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object StoryRunner {
  def props(runnerName: String)(implicit appContext: AppContext, system: ActorSystem): Props =
    Props(classOf[StoryRunner], runnerName, appContext, system)

  object Commands {
    case class Run(story: Story)
    case class Shutdown(storyNameOption: Option[String] = None)
  }
}

class StoryRunner(runnerName: String)(implicit appContext: AppContext, system: ActorSystem)
    extends Actor
    with ActorLogging
    with Timers {

  import StoryRunner.Commands._

  log.info(s"Initializing StoryRunner $runnerName")

  val _materializerSupervisionDecider: Supervision.Decider = {
    case ex: Throwable =>
      log.error(s"[Supervision] A stream stopped by exception: $ex")
      Supervision.Stop
  }
  lazy val materializer: Materializer =
    ActorMaterializer(
      ActorMaterializerSettings(system)
        .withSupervisionStrategy(_materializerSupervisionDecider)
    )(system)
  lazy val executionContext: ExecutionContext = ExecutionContext.global

  val runningStories: mutable.Map[ActorRef, String] = mutable.Map.empty

  // Supervision strategy
  val _storySupervisionDecider: PartialFunction[Throwable, Throwable] = {
    case ex: Throwable =>
      log.error(s"[Supervision] A story was running failed with error $ex")
      ex
  }
  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = 10.seconds)(
      _storySupervisionDecider andThen SupervisorStrategy.defaultDecider
    )

  override def receive: Receive = {
    case Run(story) =>
      val storyName = story.storyName
      log.info(s"Going to run the story $storyName")

      val runningContext       = TaskRunningContext(appContext, system, materializer, executionContext, self)
      val actorName            = s"story:$storyName:${java.util.UUID.randomUUID().toString}"
      val storySupervisorActor = context.actorOf(Story.props(story, self)(runningContext), actorName)

      context.watch(storySupervisorActor)
      runningStories += (storySupervisorActor -> storyName)

    case Shutdown(storyNameOption) =>
      storyNameOption match {
        case Some(storyName) =>
          runningStories.filter(_._2 == storyName).foreach(_._1 ! Story.Commands.Shutdown)
        case None =>
          runningStories.foreach(_._1 ! Story.Commands.Shutdown)
      }

    case Terminated(storyActor) =>
      log.warning(s"The StoryActor ${storyActor.path} is terminated.")
      runningStories -= storyActor
  }
}
