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
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

import scala.collection.mutable

private[pipeline] final class PipelinePool(
    poolSettings: Map[String, (PipelineType, PipelineSettings)]
)(implicit system: ActorSystem) {

  private val cached =
    mutable.Map.empty[String, Pipeline]

  private def getCache(pipelineName: String): Option[Pipeline] =
    cached.synchronized(cached.get(pipelineName))

  private def addToCache(pipeline: Pipeline): Unit =
    cached.synchronized {
      cached += (pipeline.pipelineSettings.name -> pipeline)
    }

  def getPipelineType(pipelineName: String): Option[PipelineType] =
    poolSettings.get(pipelineName) match {
      case Some((_type, _)) => Some(_type)
      case None             => None
    }

  def getPipelineSettings(pipelineName: String): Option[PipelineSettings] =
    poolSettings.get(pipelineName) match {
      case Some((_, _settings)) => Some(_settings)
      case None                 => None
    }

  def getPipelineFactory(pipelineName: String): Option[AbstractPipelineFactory] =
    poolSettings.get(pipelineName) match {
      case Some((_type, _)) => Some(AbstractPipelineFactory.getConcreteFactory(_type))
      case None             => None
    }

  /** Returns a [[Pipeline]] from [[PipelinePool]],
    * If did not existed, It creates a new [[Pipeline]] according to the predefined configuration
    * and update the [[PipelinePool]]
    *
    * @param pipelineName the predefined name of a specific pipeline
    */
  def getPipeline(pipelineName: String): Option[Pipeline] =
    getCache(pipelineName) match {
      case Some(p) => Some(p)
      case None =>
        poolSettings.get(pipelineName) match {
          case Some((_type, _settings)) =>
            val p = AbstractPipelineFactory
              .getConcreteFactory(_type)
              .createPipeline(_settings)
            addToCache(p)
            Some(p)
          case None => None
        }
    }

}

object PipelinePool {
  private var cached: Option[PipelinePool] = None

  def init(configList: List[Config])(implicit system: ActorSystem): Unit = {
    init(
      configList
        .map(config => {
          val pipelineName = config.as[String]("name")
          val pipelineType = config.as[PipelineType]("type")
          val pipelineSettings = AbstractPipelineFactory
            .getConcreteFactory(pipelineType)
            .createPipelineSettings(config)

          (pipelineName, (pipelineType, pipelineSettings))
        })
        .toMap
    )
  }

  def init(
      poolSettings: Map[String, (PipelineType, PipelineSettings)]
  )(implicit system: ActorSystem): Unit =
    cached = Some(new PipelinePool(poolSettings))

  def apply(): PipelinePool = cached match {
    case Some(f) => f
    case None =>
      throw new Exception("PipelinePool doesn't initialized yet.")
  }
}
