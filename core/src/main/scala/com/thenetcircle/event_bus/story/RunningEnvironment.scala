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

import akka.actor.ActorSystem
import com.thenetcircle.event_bus.misc.BaseEnvironment
import com.typesafe.config.Config

class RunningEnvironment(runnerGroup: String,
                         runnerId: String,
                         appName: String,
                         appVersion: String,
                         appEnv: String,
                         debug: Boolean,
                         systemConfig: Config,
                         actorSystem: ActorSystem)
    extends BaseEnvironment(
      appName: String,
      appVersion: String,
      appEnv: String,
      debug: Boolean,
      systemConfig: Config
    ) {

  def getRunnerGroup(): String = runnerGroup
  def getRunnerId(): String = runnerId
  def getActorSystem(): ActorSystem = actorSystem

}

object RunningEnvironment {

  def apply(runnerGroup: String, runnerId: String)(implicit environment: BaseEnvironment,
                                                   system: ActorSystem): RunningEnvironment = {

    new RunningEnvironment(
      runnerGroup,
      runnerId,
      environment.getAppName(),
      environment.getAppVersion(),
      environment.getAppEnv(),
      environment.isDebug(),
      environment.getConfig(),
      system
    )

  }

}
