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

final class PipelineConfigFactory(allPipelineConfig: Map[String, Config]) {
  def getPipelineType(pipelineName: String): Option[PipelineType] =
    allPipelineConfig.get(pipelineName) match {
      case Some(config) => config.as[Option[PipelineType]]("type")
      case None         => None
    }

  def getPipelineConfig(pipelineName: String): Option[Config] =
    allPipelineConfig.get(pipelineName) match {
      case Some(config) => config.as[Option[Config]]("settings")
      case None         => None
    }
}

object PipelineConfigFactory {
  private var factory: Option[PipelineConfigFactory] = None

  def initialize(system: ActorSystem): Unit =
    initialize(
      system.settings.config.as[Map[String, Config]]("event-bus.pipelines"))

  def initialize(allPipelineConfig: Map[String, Config]): Unit =
    factory = Some(new PipelineConfigFactory(allPipelineConfig))

  def apply(): PipelineConfigFactory = factory match {
    case Some(_factory) => _factory
    case None =>
      throw new Exception("PipelineConfigFactory doesn't initialized yet.")
  }
}
