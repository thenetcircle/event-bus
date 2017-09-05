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
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection.mutable

private[pipeline] final class PipelinePool(
    pipelineConfigList: Map[String, Config]) {

  private val cached = mutable.Map.empty[String, Pipeline]

  def getPipeline(pipelineName: String): Option[Pipeline] =
    cached.synchronized(cached.get(pipelineName))

  def setPipeline(pipelineName: String, pipeline: Pipeline): Unit =
    cached.synchronized {
      cached += (pipelineName -> pipeline)
    }

  def getPipelineConfig(pipelineName: String): Option[Config] =
    pipelineConfigList.get(pipelineName)

}

private[pipeline] object PipelinePool {
  private var pool: Option[PipelinePool] = None

  def initialize(system: ActorSystem): Unit = {
    val config = system.settings.config.getConfig("eventbus.pipeline")
    initialize(config)
  }

  def initialize(config: Config): Unit = {
    val settingsList = config
      .entrySet()
      .asScala
      .map { entry =>
        (entry.getKey, config.getConfig(entry.getKey))
      }
      .toMap
    pool = Some(new PipelinePool(settingsList))
  }

  def apply(): PipelinePool = pool match {
    case Some(_pool) => _pool
    case None        => throw new Exception("PipelinePool doesn't initialized yet.")
  }
}
