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

package com.thenetcircle.event_bus.story

import com.thenetcircle.event_bus.interface._
import com.thenetcircle.event_bus.misc.{BaseEnvironment, ConfigStringParser}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

class TaskBuilderFactory() {

  private var sourceTaskBuilders: Map[String, SourceTaskBuilder] = Map.empty
  private var transformTaskBuilders: Map[String, TransformTaskBuilder] = Map.empty
  private var sinkTaskBuilers: Map[String, SinkTaskBuilder] = Map.empty

  def registerBuilder(category: String, builder: TaskBuilder[Task]): Unit = {
    builder match {
      case _: SourceTaskBuilder =>
        sourceTaskBuilders += (category.toLowerCase -> builder.asInstanceOf[SourceTaskBuilder])
      case _: TransformTaskBuilder =>
        transformTaskBuilders += (category.toLowerCase -> builder
          .asInstanceOf[TransformTaskBuilder])
      case _: SinkTaskBuilder =>
        sinkTaskBuilers += (category.toLowerCase -> builder.asInstanceOf[SinkTaskBuilder])
    }
  }

  def getSourceTaskBuilder(category: String): Option[SourceTaskBuilder] =
    sourceTaskBuilders.get(category.toLowerCase)

  def getTransformTaskBuilder(category: String): Option[TransformTaskBuilder] =
    transformTaskBuilders.get(category.toLowerCase)

  def getSinkTaskBuilder(category: String): Option[SinkTaskBuilder] =
    sinkTaskBuilers.get(category.toLowerCase)

  def parseTaskConfigString(configString: String): Option[List[String]] = {
    try {
      Some(ConfigStringParser.convertStringToConfig(configString).as[List[String]](""))
    } catch {
      case _: Throwable => None
    }
  }

  def buildSourceTask(configString: String)(implicit env: BaseEnvironment): Option[SourceTask] =
    ???
  /*{
    parseTaskConfigString(configString).map {
      case _category :: _configString :: _ => buildTaskA(_category, _configString)
    }
  }*/

  def buildSourceTask(category: String,
                      configString: String)(implicit env: BaseEnvironment): Option[SourceTask] =
    getSourceTaskBuilder(category).map(_builder => _builder.build(configString))

  def buildTransformTask(
      configString: String
  )(implicit env: BaseEnvironment): Option[TransformTask] =
    ???

  def buildTransformTask(category: String, configString: String)(
      implicit env: BaseEnvironment
  ): Option[TransformTask] =
    getTransformTaskBuilder(category).map(_builder => _builder.build(configString))

  def buildSinkTask(configString: String)(implicit env: BaseEnvironment): Option[SinkTask] =
    ???

  def buildSinkTask(category: String,
                    configString: String)(implicit env: BaseEnvironment): Option[SinkTask] =
    getSinkTaskBuilder(category).map(_builder => _builder.build(configString))

}

object TaskBuilderFactory {

  def apply(config: Config): TaskBuilderFactory = {

    val builderFactory = new TaskBuilderFactory()

    config.checkValid(ConfigFactory.defaultReference, "app.supported-builders")

    List("source", "transform", "sink").foreach(prefix => {
      config
        .as[List[List[String]]](s"app.supported-builders.$prefix")
        .foreach {
          case plotType :: builderClass :: _ =>
            builderFactory.registerBuilder(
              plotType,
              Class.forName(builderClass).asInstanceOf[Class[TaskBuilder[Task]]].newInstance()
            )
          case _ =>
        }
    })

    builderFactory

  }

}
