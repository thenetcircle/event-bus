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

package com.thenetcircle.event_bus.pipeline
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config

trait AbstractPipelineFactory {

  type LPS <: LeftPortSettings
  type RPS <: RightPortSettings

  /** Creates [[PipelineSettings]] according to a TypeSafe [[Config]]
    *
    * @param pipelineConfig the TypeSafe [[Config]]
    */
  def getPipelineSettings(pipelineConfig: Config)(
      implicit system: ActorSystem): PipelineSettings

  /** Returns a [[Pipeline]] from [[PipelinePool]],
    * If did not existed, It creates a new [[Pipeline]] according to the predefined configuration
    * and update the [[PipelinePool]]
    *
    * @param pipelineName
    */
  def getPipeline(pipelineName: String)(
      implicit system: ActorSystem): Option[Pipeline]

  /** Creates [[LeftPortSettings]] according to a TypeSafe [[Config]]
    *
    * @param leftPortConfig the TypeSafe [[Config]]
    */
  def getLeftPortSettings(leftPortConfig: Config): LeftPortSettings

  /** Returns a [[LeftPort]] of the [[Pipeline]] which has the pipelineName with the leftPortConfig
    *
    * @param pipelineName uses for get a created [[Pipeline]] or create a new [[Pipeline]] based on predefined [[Config]]
    * @param leftPortSettings the settings of the [[LeftPort]]
    *
    * @return [[LeftPort]]
    */
  def getLeftPort(pipelineName: String, leftPortSettings: LPS)(
      implicit system: ActorSystem): Option[LeftPort]

  /** Creates [[RightPortSettings]] according to a TypeSafe [[Config]]
    *
    * @param rightPortConfig the TypeSafe [[Config]]
    */
  def getRightPortSettings(rightPortConfig: Config): RightPortSettings

  /** Returns a [[RightPort]] of the [[Pipeline]] which has the pipelineName with the rightPortConfig
    *
    * @param pipelineName uses for get a created [[Pipeline]] or create a new [[Pipeline]] based on predefined [[Config]]
    * @param rightPortSettings the settings of the [[RightPort]]
    *
    * @return [[RightPort]]
    */
  def getRightPort(pipelineName: String, rightPortSettings: RPS)(
      implicit system: ActorSystem,
      materializer: Materializer): Option[RightPort]

}
