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
    storyRunner: ActorRef
) {
  def getAppContext(): AppContext             = appContext
  def getActorSystem(): ActorSystem           = system
  def getMaterializer(): Materializer         = materializer
  def getExecutionContext(): ExecutionContext = executionContext
  def getStoryRunenr(): ActorRef              = storyRunner
}

object TaskRunningContext {

  def apply(
      appContext: AppContext,
      system: ActorSystem,
      materializer: Materializer,
      executionContext: ExecutionContext,
      storyRunner: ActorRef
  ): TaskRunningContext =
    new TaskRunningContext(appContext, system, materializer, executionContext, storyRunner)

}
