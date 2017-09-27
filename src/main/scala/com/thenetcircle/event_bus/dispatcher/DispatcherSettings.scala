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

package com.thenetcircle.event_bus.dispatcher

import akka.actor.ActorSystem
import akka.stream.ActorMaterializerSettings
import com.thenetcircle.event_bus.dispatcher.endpoint.EndPointSettings
import com.thenetcircle.event_bus.pipeline.{
  Pipeline,
  PipelineFactory,
  PipelineOutletSettings,
  PipelinePool
}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

object DispatcherSettings extends StrictLogging {

  def apply(_config: Config)(implicit system: ActorSystem): DispatcherSettings = {
    val config: Config =
      _config.withFallback(
        system.settings.config.getConfig("event-bus.dispatcher"))

    val name               = config.as[String]("name")
    val maxParallelSources = config.as[Int]("max-parallel-sources")

    val endPointSettings =
      config.as[Vector[Config]]("endpoints").map(EndPointSettings(_))

    val pipelineName = config.as[String]("pipeline.name")
    val pipeline     = PipelinePool().getPipeline(pipelineName).get
    val pipelineOutletSettings = PipelineFactory
      .getConcreteFactory(pipeline.pipelineType)
      .createPipelineOutletSettings(
        config.as[Config]("pipeline.outlet-settings"))

    val materializerKey = "akka.stream.materializer"
    val materializerSettings: Option[ActorMaterializerSettings] = {
      if (config.hasPath(materializerKey))
        Some(
          ActorMaterializerSettings(
            config
              .getConfig(materializerKey)
              .withFallback(system.settings.config
                .getConfig(materializerKey))))
      else
        None
    }

    DispatcherSettings(name,
                       maxParallelSources,
                       endPointSettings,
                       pipeline,
                       pipelineOutletSettings,
                       materializerSettings)

  }
}

final case class DispatcherSettings(
    name: String,
    maxParallelSources: Int,
    endPointSettings: Vector[EndPointSettings],
    pipeline: Pipeline,
    pipelineOutletSettings: PipelineOutletSettings,
    materializerSettings: Option[ActorMaterializerSettings]
)
