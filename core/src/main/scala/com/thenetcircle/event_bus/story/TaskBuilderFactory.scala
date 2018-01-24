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

import com.thenetcircle.event_bus.interfaces._
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

class TaskBuilderFactory() {
  private var sourceTaskBuilders: Map[String, SourceTaskBuilder]       = Map.empty
  private var transformTaskBuilders: Map[String, TransformTaskBuilder] = Map.empty
  private var sinkTaskBuilers: Map[String, SinkTaskBuilder]            = Map.empty
  private var fallbackTaskBuilers: Map[String, FallbackTaskBuilder]    = Map.empty

  private def createInstance(cls: Class[TaskBuilder[Task]]): TaskBuilder[Task] = cls.newInstance()

  def registerBuilder(category: String, builderClassName: String): Unit =
    registerBuilder(
      category,
      Class.forName(builderClassName).asInstanceOf[Class[TaskBuilder[Task]]]
    )

  def registerBuilder(category: String, builderClass: Class[TaskBuilder[Task]]): Unit =
    registerBuilder(category, createInstance(builderClass))

  def registerBuilder(category: String, builder: TaskBuilder[Task]): Unit =
    builder match {
      case _: SourceTaskBuilder =>
        sourceTaskBuilders += (category.toLowerCase -> builder.asInstanceOf[SourceTaskBuilder])
      case _: TransformTaskBuilder =>
        transformTaskBuilders += (category.toLowerCase -> builder
          .asInstanceOf[TransformTaskBuilder])
      case _: SinkTaskBuilder =>
        sinkTaskBuilers += (category.toLowerCase -> builder.asInstanceOf[SinkTaskBuilder])
      case _: FallbackTaskBuilder =>
        fallbackTaskBuilers += (category.toLowerCase -> builder.asInstanceOf[FallbackTaskBuilder])
    }

  def getSourceTaskBuilder(category: String): Option[SourceTaskBuilder] =
    sourceTaskBuilders.get(category.toLowerCase)

  def getTransformTaskBuilder(category: String): Option[TransformTaskBuilder] =
    transformTaskBuilders.get(category.toLowerCase)

  def getSinkTaskBuilder(category: String): Option[SinkTaskBuilder] =
    sinkTaskBuilers.get(category.toLowerCase)

  def getFallbackTaskBuilder(category: String): Option[FallbackTaskBuilder] =
    fallbackTaskBuilers.get(category.toLowerCase)
}

object TaskBuilderFactory {
  def apply(config: Config): TaskBuilderFactory = {
    config.checkValid(ConfigFactory.defaultReference, "task.builders")

    val taskBuilderFactory = new TaskBuilderFactory()
    List("source", "transform", "sink", "fallback").foreach(prefix => {
      config
        .as[List[List[String]]](s"task.builders.$prefix")
        .foreach {
          case category :: builderClassName :: _ =>
            taskBuilderFactory.registerBuilder(category, builderClassName)
          case _ =>
        }
    })
    taskBuilderFactory
  }
}
