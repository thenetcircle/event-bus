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

package com.thenetcircle.event_bus.story.builder

import com.thenetcircle.event_bus.story.interfaces._
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

class TaskBuilderFactory() {

  private var sourceTaskBuilders: Map[String, ISourceTaskBuilder]       = Map.empty
  private var transformTaskBuilders: Map[String, ITransformTaskBuilder] = Map.empty
  private var sinkTaskBuilers: Map[String, ISinkTaskBuilder]            = Map.empty
  private var fallbackTaskBuilers: Map[String, IFallbackTaskBuilder]    = Map.empty

  private def createInstance(cls: Class[ITaskBuilder[ITask]]): ITaskBuilder[ITask] = cls.newInstance()

  def registerBuilder(category: String, builderClassName: String): Unit =
    registerBuilder(
      category,
      Class.forName(builderClassName).asInstanceOf[Class[ITaskBuilder[ITask]]]
    )

  def registerBuilder(category: String, builderClass: Class[ITaskBuilder[ITask]]): Unit =
    registerBuilder(category, createInstance(builderClass))

  def registerBuilder(category: String, builder: ITaskBuilder[ITask]): Unit =
    builder match {
      case _: ISourceTaskBuilder =>
        sourceTaskBuilders += (category.toLowerCase -> builder.asInstanceOf[ISourceTaskBuilder])
      case _: ITransformTaskBuilder =>
        transformTaskBuilders += (category.toLowerCase -> builder
          .asInstanceOf[ITransformTaskBuilder])
      case _: ISinkTaskBuilder =>
        sinkTaskBuilers += (category.toLowerCase -> builder.asInstanceOf[ISinkTaskBuilder])
      case _: IFallbackTaskBuilder =>
        fallbackTaskBuilers += (category.toLowerCase -> builder.asInstanceOf[IFallbackTaskBuilder])
    }

  def getSourceTaskBuilder(category: String): Option[ISourceTaskBuilder] =
    sourceTaskBuilders.get(category.toLowerCase)

  def getTransformTaskBuilder(category: String): Option[ITransformTaskBuilder] =
    transformTaskBuilders.get(category.toLowerCase)

  def getSinkTaskBuilder(category: String): Option[ISinkTaskBuilder] =
    sinkTaskBuilers.get(category.toLowerCase)

  def getFallbackTaskBuilder(category: String): Option[IFallbackTaskBuilder] =
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
