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
import akka.stream.Materializer
import com.thenetcircle.event_bus.pipeline.{Pipeline, PipelineFactory}
import com.thenetcircle.event_bus.transporter.entrypoint.{
  EntryPoint,
  EntryPointFactory
}
import com.typesafe.config.Config

import scala.collection.JavaConverters._

object TransporterSettings {
  def apply(
      config: Config
  )(implicit system: ActorSystem,
    materializer: Materializer): TransporterSettings = {

    val name = config.getString("name")

    val entryPoints: Set[EntryPoint] = {
      for (_ec <- config.getConfigList("entry_points").asScala)
        yield EntryPointFactory.createEntryPoint(_ec)
    }.toSet

    val pipelineName = config.getString("pipeline")
    val pipeline     = PipelineFactory.getPipeline(pipelineName)

    apply(name, entryPoints, pipeline)

  }

  def apply(
      name: String,
      entryPoints: Set[EntryPoint],
      pipeline: Pipeline
  ): TransporterSettings = new TransporterSettings(name, entryPoints, pipeline)
}

final case class TransporterSettings(
    name: String,
    entryPoints: Set[EntryPoint],
    pipeline: Pipeline
)
