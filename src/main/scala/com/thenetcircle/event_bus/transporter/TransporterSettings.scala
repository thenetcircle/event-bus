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

package com.thenetcircle.event_bus.transporter

import akka.actor.ActorSystem
import akka.stream.ActorMaterializerSettings
import com.thenetcircle.event_bus.EventFormat
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointSettings
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._

object TransporterSettings extends StrictLogging {

  // TODO adjust these default values when doing stress testing
  val defaultMaxParallelSourcesPerEntryPoint = 100

  def apply(config: Config)(implicit system: ActorSystem): TransporterSettings = {

    val name = config.getString("name")

    val transportEntryPointsSettings: Vector[TransporterEntryPointSettings] = {
      for (epConfig <- config.getConfigList("entry-points-settings").asScala)
        yield {
          val maxParallelSources =
            if (epConfig.hasPath("max-parallel-sources"))
              epConfig.getInt("max-parallel-sources")
            else defaultMaxParallelSourcesPerEntryPoint

          val eventFormat =
            if (epConfig.hasPath("format"))
              EventFormat(epConfig.getString("format"))
            else EventFormat.DefaultFormat

          TransporterEntryPointSettings(
            maxParallelSources,
            eventFormat,
            EntryPointSettings(epConfig)
          )
        }
    }.toVector

    val pipelineConfig         = config.getConfig("pipeline")
    val pipelineName           = pipelineConfig.getString("name")
    val pipelineLeftPortConfig = pipelineConfig.getConfig("left-port")

    val commitParallelism = config.getInt("commit-parallelism")

    val materializerSettings: Option[ActorMaterializerSettings] = try {
      if (config.hasPath("materializer")) {
        val _mc = config.getConfig("materializer")
        val _defaultMc =
          system.settings.config.getConfig("akka.stream.materializer")
        Some(ActorMaterializerSettings(_mc.withFallback(_defaultMc)))
      } else {
        None
      }
    } catch {
      case _: Exception =>
        logger.warn("Configured materializer is not correct.")
        None
    }

    TransporterSettings(name,
                        transportEntryPointsSettings,
                        pipelineName,
                        pipelineLeftPortConfig,
                        commitParallelism,
                        materializerSettings)

  }
}

final case class TransporterSettings(
    name: String,
    transportEntryPointsSettings: Vector[TransporterEntryPointSettings],
    pipelineName: String,
    pipelineLeftPortConfig: Config,
    commitParallelism: Int,
    materializerSettings: Option[ActorMaterializerSettings]
)
