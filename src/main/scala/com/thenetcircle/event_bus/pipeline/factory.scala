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

import akka.stream.Materializer
import com.thenetcircle.event_bus.event_extractor.EventExtractor
import com.thenetcircle.event_bus.pipeline.Pipeline.{LeftPort, RightPort}
import com.thenetcircle.event_bus.pipeline.kafka.{
  KafkaPipeline,
  KafkaPipelineSettings
}
import com.typesafe.config.Config

/**  Does create [[Pipeline]] based on predefined [[Config]], Caches created [[Pipeline]] for next demand */
object PipelineFactory {

  private var allPipelines         = Map.empty[String, Pipeline]
  private var allPipelinesSettings = Map.empty[String, PipelineSettings]

  def init(pipelineSettings: Set[PipelineSettings]): Unit = synchronized {
    allPipelinesSettings = pipelineSettings.map(s => s.name -> s).toMap
  }

  def init(configList: Vector[Config]): Unit =
    init(configList.map(PipelineSettingsFactory(_)).toSet)

  def getPipeline(name: String): Option[Pipeline] = synchronized {
    allPipelines.get(name) match {
      case Some(pipeline) => Some(pipeline)
      case None =>
        createPipeline(name) match {
          case Some(pipeline) =>
            allPipelines += (name -> pipeline)
            Some(pipeline)
          case None => None
        }
    }
  }

  /** Returns a [[LeftPort]] of the [[Pipeline]] which has the pipelineName with the leftPortConfig
    *
    * @param pipelineName uses for get a created [[Pipeline]] or create a new [[Pipeline]] based on predefined [[Config]]
    * @param leftPortConfig uses for create a [[LeftPortSettings]] from [[LeftPortSettings]]'s factory
    *
    * @return [[LeftPort]]
    */
  def getLeftPort(pipelineName: String, leftPortConfig: Config): LeftPort =
    getPipeline(pipelineName) match {
      case Some(pipeline) =>
        val leftPortSettings = LeftPortSettingsFactory(leftPortConfig)
        pipeline.leftPort(leftPortSettings)
      case None =>
        throw new IllegalArgumentException(
          s"Pipeline $pipelineName does not exists.")
    }

  /** Returns a [[RightPort]] of the [[Pipeline]] which has the pipelineName with the rightPortConfig
    *
    * @param pipelineName uses for get a created [[Pipeline]] or create a new [[Pipeline]] based on predefined [[Config]]
    * @param rightPortConfig uses for create a [[RightPortSettings]] from [[RightPortSettings]]'s factory
    *
    * @return [[RightPort]]
    */
  def getRightPort(pipelineName: String, rightPortConfig: Config)(
      implicit materializer: Materializer): RightPort =
    getPipeline(pipelineName) match {
      case Some(pipeline) =>
        val rightPortSettings  = RightPortSettingsFactory(rightPortConfig)
        implicit val extractor = EventExtractor(rightPortSettings.eventFormat)
        pipeline.rightPort(rightPortSettings)
      case None =>
        throw new IllegalArgumentException(
          s"Pipeline $pipelineName does not exists.")
    }

  private def createPipeline(name: String): Option[Pipeline] =
    allPipelinesSettings.get(name) match {
      case Some(pipelineSettings) =>
        pipelineSettings match {
          case s: KafkaPipelineSettings => Some(KafkaPipeline(s))
          case _                        => None
        }
      case None => None
    }

}

/** Does create [[PipelineSettings]] according to a TypeSafe [[Config]] */
object PipelineSettingsFactory {

  /** Returns a [[PipelineSettings]]
    *
    * @param config the TypeSafe [[Config]]
    */
  def apply(config: Config): PipelineSettings = ???
}

/** Does create [[LeftPortSettings]] according to a TypeSafe [[Config]] */
object LeftPortSettingsFactory {

  /** Returns a [[LeftPortSettings]]
    *
    * @param config the TypeSafe [[Config]]
    */
  def apply(config: Config): LeftPortSettings = ???
}

/** Does create [[RightPortSettings]] according to a TypeSafe [[Config]] */
object RightPortSettingsFactory {

  /** Returns a [[RightPortSettings]]
    *
    * @param config the TypeSafe [[Config]]
    */
  def apply(config: Config): RightPortSettings = ???
}
