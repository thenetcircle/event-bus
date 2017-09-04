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
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

object DispatcherSettings extends StrictLogging {

  def apply(config: Config)(implicit system: ActorSystem): DispatcherSettings = {

    val name = config.getString("name")

    val endPointSettings = EndPointSettings(config.getConfig("endpoint"))

    val pipelineConfig         = config.getConfig("pipeline")
    val pipelineName           = pipelineConfig.getString("name")
    val pipelineLeftPortConfig = pipelineConfig.getConfig("right-port")

    // TODO: adjust these default values when doing stress testing
    // TODO: use reference.conf to set up default value
    val maxParallelSources =
      if (config.hasPath("max-parallel-sources"))
        config.getInt("max-parallel-sources")
      else 100

    val materializerSettings: Option[ActorMaterializerSettings] =
      if (config.hasPath("materializer")) {
        Some(
          ActorMaterializerSettings(
            config
              .getConfig("materializer")
              .withFallback(system.settings.config
                .getConfig("akka.stream.materializer"))))
      } else {
        None
      }

    DispatcherSettings(name,
                       maxParallelSources,
                       endPointSettings,
                       pipelineName,
                       pipelineLeftPortConfig,
                       materializerSettings)

  }
}

final case class DispatcherSettings(
    name: String,
    maxParallelSources: Int,
    endPointSettings: EndPointSettings,
    pipelineName: String,
    pipelineRightPortConfig: Config,
    materializerSettings: Option[ActorMaterializerSettings]
)
