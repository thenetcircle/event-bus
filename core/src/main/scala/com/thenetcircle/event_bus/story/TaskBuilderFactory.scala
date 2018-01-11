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
import com.thenetcircle.event_bus.misc.ConfigStringParser
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

class TaskBuilderFactory() {

  private var taskABuilders: Map[String, TaskABuilder] = Map.empty
  private var taskBBuilders: Map[String, TaskBBuilder] = Map.empty
  private var taskCBuilers: Map[String, TaskCBuilder] = Map.empty

  def registerBuilder(category: String, builder: TaskBuilder[Task]): Unit = {
    builder match {
      case _: TaskABuilder =>
        taskABuilders += (category.toLowerCase -> builder.asInstanceOf[TaskABuilder])
      case _: TaskBBuilder =>
        taskBBuilders += (category.toLowerCase -> builder.asInstanceOf[TaskBBuilder])
      case _: TaskCBuilder =>
        taskCBuilers += (category.toLowerCase -> builder.asInstanceOf[TaskCBuilder])
    }
  }

  def getTaskABuilder(category: String): Option[TaskABuilder] =
    taskABuilders.get(category.toLowerCase)

  def getTaskBBuilder(opType: String): Option[TaskBBuilder] = taskBBuilders.get(opType.toLowerCase)

  def getTaskCBuilder(sinkType: String): Option[TaskCBuilder] =
    taskCBuilers.get(sinkType.toLowerCase)

  def parseTaskConfigString(configString: String): Option[List[String]] = {
    try {
      Some(ConfigStringParser.convertStringToConfig(configString).as[List[String]](""))
    } catch {
      case _: Throwable => None
    }
  }

  def buildTaskA(configString: String)(implicit context: TaskExecutingContext): Option[TaskA] = {
    parseTaskConfigString(configString).map {
      case _category :: _configString :: _ => buildTaskA(_category, _configString)
    }
  }

  def buildTaskA(category: String,
                 configString: String)(implicit context: TaskExecutingContext): Option[TaskA] =
    getTaskABuilder(category).map(_builder => _builder.build(configString))

  def buildTaskB(category: String,
                 configString: String)(implicit context: TaskExecutingContext): Option[TaskB] =
    getTaskBBuilder(category).map(_builder => _builder.build(configString))

  def buildTaskC(category: String,
                 configString: String)(implicit context: TaskExecutingContext): Option[TaskC] =
    getTaskCBuilder(category).map(_builder => _builder.build(configString))

}

object TaskBuilderFactory {

  def apply(config: Config): TaskBuilderFactory = {

    val builderFactory = new TaskBuilderFactory()

    config.checkValid(ConfigFactory.defaultReference, "app.supported-builders")

    List("A", "B", "C").foreach(prefix => {
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
