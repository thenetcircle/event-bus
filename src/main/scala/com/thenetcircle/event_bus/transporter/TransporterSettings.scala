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
import com.thenetcircle.event_bus.pipeline.{
  AbstractPipelineFactory,
  LeftPortSettings
}
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointSettings
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.collection.JavaConverters._

object TransporterSettings extends StrictLogging {
  def apply(system: ActorSystem): TransporterSettings =
    apply(system.settings.config.getConfig("transporter"))(system)

  def apply(config: Config)(implicit system: ActorSystem): TransporterSettings = {

    val name = config.getString("name")

    val entryPointsSettings: Vector[EntryPointSettings] = {
      for (_config <- config.getConfigList("entrypoints-settings").asScala)
        yield EntryPointSettings(_config)
    }.toVector

    val pipelineFactory = config.as[AbstractPipelineFactory]("pipeline")
    val pipelineName    = config.getString("pipeline.name")
    val pipelineLeftPortSettings =
      pipelineFactory.getLeftPortSettings(config.getConfig("pipeline.leftport"))

    val transportParallelism = config.getInt("transport-parallelism")
    val commitParallelism    = config.getInt("commit-parallelism")

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

    TransporterSettings(name,
                        commitParallelism,
                        transportParallelism,
                        entryPointsSettings,
                        pipelineFactory,
                        pipelineName,
                        pipelineLeftPortSettings,
                        materializerSettings)

  }
}

final case class TransporterSettings(
    name: String,
    commitParallelism: Int,
    transportParallelism: Int = 1,
    entryPointsSettings: Vector[EntryPointSettings],
    pipelineFactory: AbstractPipelineFactory,
    pipelineName: String,
    pipelineLeftPortSettings: LeftPortSettings,
    materializerSettings: Option[ActorMaterializerSettings]
)
