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
  LeftPortSettings,
  Pipeline,
  PipelinePool
}
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointSettings
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

object TransporterSettings extends StrictLogging {
  def apply(config: Config)(implicit system: ActorSystem): TransporterSettings = {
    val name = config.as[String]("name")

    val entryPointsSettings: Vector[EntryPointSettings] =
      for (_config <- config.as[Vector[Config]]("entrypoints"))
        yield EntryPointSettings(_config)

    val pipelineName    = config.as[String]("pipeline.name")
    val pipelineFactory = PipelinePool().getPipelineFactory(pipelineName).get
    val pipeline        = pipelineFactory.getPipeline(pipelineName).get
    val leftPortSettings = pipelineFactory.getLeftPortSettings(
      config.as[Config]("pipeline.leftport"))

    val transportParallelism = config.as[Int]("transport-parallelism")
    val commitParallelism    = config.as[Int]("commit-parallelism")

    val materializerKey = "akka.stream.materializer"
    val materializerSettings: Option[ActorMaterializerSettings] = {
      if (config.hasPath(materializerKey))
        Some(
          ActorMaterializerSettings(
            config
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
                        leftPortSettings,
                        materializerSettings)

  }
}

final case class TransporterSettings(
    name: String,
    commitParallelism: Int,
    transportParallelism: Int = 1,
    entryPointsSettings: Vector[EntryPointSettings],
    pipeline: Pipeline,
    leftPortSettings: LeftPortSettings,
    materializerSettings: Option[ActorMaterializerSettings]
)
