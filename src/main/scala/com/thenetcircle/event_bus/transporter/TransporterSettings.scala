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
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointSettings
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.collection.JavaConverters._

object TransporterSettings extends StrictLogging {
  def apply(config: Config)(implicit system: ActorSystem): TransporterSettings = {

    val name = config.getString("name")

    val entryPointsSettings: Vector[EntryPointSettings] = {
      for (_config <- config.getConfigList("entry_points_settings").asScala)
        yield EntryPointSettings(_config)
    }.toVector

    val pipelineName = config.getString("pipeline_name")

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
                        entryPointsSettings,
                        pipelineName,
                        materializerSettings)

  }
}

final case class TransporterSettings(
    name: String,
    entryPointsSettings: Vector[EntryPointSettings],
    pipelineName: String,
    materializerSettings: Option[ActorMaterializerSettings]
)
