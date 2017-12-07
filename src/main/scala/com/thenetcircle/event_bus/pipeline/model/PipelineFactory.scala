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

package com.thenetcircle.event_bus.pipeline.model

import akka.actor.ActorSystem
import com.thenetcircle.event_bus.pipeline.kafka.KafkaPipelineFactory
import com.thenetcircle.event_bus.pipeline.model.PipelineType.PipelineType
import com.typesafe.config.Config

abstract class PipelineFactory(implicit system: ActorSystem) {

  /** Creates a new [[Pipeline]]
    *
    * @param pipelineSettings the settings of pipeline
    */
  def createPipeline(pipelineSettings: PipelineSettings): Pipeline

  /** Creates [[PipelineSettings]] according to a TypeSafe [[Config]]
    *
    * @param pipelineConfig the TypeSafe [[Config]]
    */
  def createPipelineSettings(pipelineConfig: Config): PipelineSettings

  /** Creates [[PipelineInletSettings]] according to a TypeSafe [[Config]]
    *
    * @param pipelineInletConfig the TypeSafe [[Config]]
    */
  def createPipelineInletSettings(pipelineInletConfig: Config): PipelineInletSettings

  /** Creates [[PipelineOutletSettings]] according to a TypeSafe [[Config]]
    *
    * @param pipelineOutletConfig the TypeSafe [[Config]]
    */
  def createPipelineOutletSettings(pipelineOutletConfig: Config): PipelineOutletSettings
}

object PipelineFactory {
  def getConcreteFactory(
      pipelineType: PipelineType
  )(implicit system: ActorSystem): PipelineFactory =
    pipelineType match {
      case PipelineType.Kafka => KafkaPipelineFactory()
      case _ =>
        throw new Exception(s"""No matched pipeline factory of pipeline type "$pipelineType".""")
    }
}
