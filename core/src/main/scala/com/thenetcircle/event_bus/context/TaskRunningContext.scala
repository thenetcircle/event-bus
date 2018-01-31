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

package com.thenetcircle.event_bus.context

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Materializer, Supervision}
import com.thenetcircle.event_bus.story.StorySettings
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext

class TaskRunningContext(
    appContext: AppContext,
    system: ActorSystem,
    materializer: Materializer,
    executionContext: ExecutionContext,
    storyRunnerName: String,
    storyRunner: ActorRef,
    storySettings: StorySettings
) {

  def getAppContext(): AppContext             = appContext
  def getActorSystem(): ActorSystem           = system
  def getMaterializer(): Materializer         = materializer
  def getExecutionContext(): ExecutionContext = executionContext
  def getStoryRunnerName(): String            = storyRunnerName
  def getStoryRunner(): ActorRef              = storyRunner
  def getStoryName(): String                  = storySettings.name

}

class TaskRunningContextFactory(system: ActorSystem, appContext: AppContext) extends LazyLogging {
  val decider: Supervision.Decider = {
    case ex: Throwable =>
      logger.error(s"materializer supervision triggered by exception: $ex")
      Supervision.Stop
  }

  lazy val materializer: Materializer =
    ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))(system)

  lazy val executionContext: ExecutionContext = ExecutionContext.global

  def createNewRunningContext(
      storyRunnerName: String,
      storyRunner: ActorRef,
      storySettings: StorySettings
  ): TaskRunningContext =
    new TaskRunningContext(
      appContext,
      system,
      materializer,
      executionContext,
      storyRunnerName,
      storyRunner,
      storySettings
    )
}

object TaskRunningContextFactory {
  def apply(system: ActorSystem, appContext: AppContext): TaskRunningContextFactory =
    new TaskRunningContextFactory(system, appContext)
}
