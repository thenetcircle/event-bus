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
import com.thenetcircle.event_bus.pipeline.PipelineType.PipelineType
import com.thenetcircle.event_bus.pipeline.kafka.KafkaPipelineFactory
import com.typesafe.config.Config

import scala.collection.mutable

abstract class PipelineFactory(implicit system: ActorSystem) {

  protected val cached =
    mutable.Map.empty[String, Pipeline]

  protected def getCache(pipelineName: String): Option[Pipeline] =
    cached.synchronized(cached.get(pipelineName))

  protected def addToCache(pipelineName: String, pipeline: Pipeline): Unit =
    cached.synchronized {
      cached += (pipelineName -> pipeline)
    }

  /** Creates [[PipelineSettings]] according to the predefined TypeSafe [[Config]]
    *
    * @param pipelineName the predefined name of a pipeline
    */
  def getPipelineSettings(pipelineName: String): PipelineSettings

  /** Returns a [[Pipeline]] from [[PipelineConfigPool]],
    * If did not existed, It creates a new [[Pipeline]] according to the predefined configuration
    * and update the [[PipelineConfigPool]]
    *
    * @param pipelineName the predefined name of a specific pipeline
    */
  def getPipeline(pipelineName: String): Pipeline

  /** Creates [[PipelineInletSettings]] according to a TypeSafe [[Config]]
    *
    * @param pipelineName
    * @param pipelineInletConfig the TypeSafe [[Config]]
    */
  def getPipelineInletSettings(
      pipelineName: String,
      pipelineInletConfig: Config): PipelineInletSettings

  /** Creates [[PipelineOutletSettings]] according to a TypeSafe [[Config]]
    *
    * @param pipelineName
    * @param pipelineOutletConfig the TypeSafe [[Config]]
    */
  def getPipelineOutletSettings(
      pipelineName: String,
      pipelineOutletConfig: Config): PipelineOutletSettings

}

object PipelineFactory {
  def getConcreteFactoryByName(pipelineName: String)(
      implicit system: ActorSystem): PipelineFactory =
    PipelineConfigPool().getPipelineType(pipelineName) match {
      case Some(pipelineType) =>
        PipelineFactory.getConcreteFactoryByType(pipelineType)
      case None =>
        throw new Exception(
          s"""No matched pipeline factory of pipeline name "$pipelineName".""")
    }

  def getConcreteFactoryByType(pipelineType: PipelineType)(
      implicit system: ActorSystem): PipelineFactory =
    pipelineType match {
      case PipelineType.Kafka => KafkaPipelineFactory()
      case _ =>
        throw new Exception(
          s"""No matched pipeline factory of pipeline type "$pipelineType".""")
    }
}
