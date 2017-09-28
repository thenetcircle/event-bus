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
  Pipeline,
  PipelineFactory,
  PipelineInletSettings,
  PipelinePool
}
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointSettings
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

object TransporterSettings extends StrictLogging {
  def apply(_config: Config)(
      implicit system: ActorSystem): TransporterSettings = {
    val config: Config =
      _config.withFallback(
        system.settings.config.getConfig("event-bus.transporter"))

    logger.info(
      s"Creating a new TransporterSettings accroding to config: $config")

    try {
      val name = config.as[String]("name")

      val entryPointsSettings =
        config.as[Vector[Config]]("entrypoints").map(EntryPointSettings(_))

      val pipelineName = config.as[String]("pipeline.name")
      val pipeline     = PipelinePool().getPipeline(pipelineName).get
      val pipelineInletSettings = PipelineFactory
        .getConcreteFactory(pipeline.pipelineType)
        .createPipelineInletSettings(
          config
            .as[Option[Config]]("pipeline.inlet")
            .getOrElse(ConfigFactory.empty()))

      val transportParallelism = config.as[Int]("transport-parallelism")
      val commitParallelism    = config.as[Int]("commit-parallelism")

      val materializerKey = "akka.stream.materializer"
      val materializerSettings: Option[ActorMaterializerSettings] = {
        if (config.hasPath(materializerKey))
          Some(
            ActorMaterializerSettings(config
              .getConfig(materializerKey)
              .withFallback(system.settings.config.getConfig(materializerKey))))
        else
          None
      }

      TransporterSettings(name,
                          commitParallelism,
                          transportParallelism,
                          entryPointsSettings,
                          pipeline,
                          pipelineInletSettings,
                          materializerSettings)
    } catch {
      case ex: Throwable =>
        logger.error(
          s"Creating TransporterSettings failed with error: ${ex.getMessage}")
        throw ex
    }
  }
}

final case class TransporterSettings(
    name: String,
    commitParallelism: Int,
    transportParallelism: Int,
    entryPointsSettings: Vector[EntryPointSettings],
    pipeline: Pipeline,
    pipelineInletSettings: PipelineInletSettings,
    materializerSettings: Option[ActorMaterializerSettings]
)
