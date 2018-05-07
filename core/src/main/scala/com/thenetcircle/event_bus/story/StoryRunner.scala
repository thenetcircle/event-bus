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
import com.thenetcircle.event_bus.context.{AppContext, TaskRunningContextFactory}

import scala.collection.mutable
import scala.concurrent.duration._

object StoryRunner {
  def props(runnerName: String)(implicit appContext: AppContext, system: ActorSystem): Props =
    Props(classOf[StoryRunner], runnerName, appContext, system)

  case class Run(story: Story)
  case class Shutdown(storyNameOption: Option[String] = None)
}

class StoryRunner(runnerName: String)(implicit appContext: AppContext, system: ActorSystem)
    extends Actor
    with ActorLogging
    with Timers {

  import StoryRunner._

  log.info(s"------ story runner $runnerName is starting ------")

  val runningContextFactory: TaskRunningContextFactory =
    TaskRunningContextFactory(system, appContext)

  val runningStories: mutable.Map[ActorRef, String] = mutable.Map.empty

  // Supervision strategy
  val loggerSupervistionDecider: PartialFunction[Throwable, Throwable] = {
    case ex: Throwable =>
      log.error(s"a story was failed with error $ex, now will go through SupervisorStrategy")
      ex
  }
  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 5, withinTimeRange = 10.seconds)(
      loggerSupervistionDecider andThen SupervisorStrategy.defaultDecider
    )

  override def receive: Receive = {
    case Run(story) =>
      val storyName = story.storyName
      log.info(s"preparing to run story $storyName")
      val runningContext =
        runningContextFactory.createNewRunningContext(runnerName, self, story.settings)

      val actorName = s"story:$storyName:${java.util.UUID.randomUUID().toString}"
      val storyActor =
        context.actorOf(StoryActor.props(story, self)(runningContext), actorName)
      context.watch(storyActor)

      runningStories += (storyActor -> storyName)

    case Shutdown(storyNameOption) =>
      storyNameOption match {
        case Some(storyName) =>
          runningStories.filter(_._2 == storyName).foreach(_._1 ! StoryActor.Shutdown)
        case None =>
          runningStories.foreach(_._1 ! StoryActor.Shutdown)
      }

    case Terminated(storyActor) =>
      log.warning(s"story actor ${storyActor.path} is terminated.")
      runningStories -= storyActor
  }
}
