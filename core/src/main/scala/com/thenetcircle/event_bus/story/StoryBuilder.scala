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

import com.thenetcircle.event_bus.AppContext
import com.thenetcircle.event_bus.misc.Util
import com.thenetcircle.event_bus.story.interfaces._
import com.typesafe.scalalogging.LazyLogging

import scala.reflect.runtime.universe._
import scala.util.matching.Regex

class StoryBuilder()(implicit appContext: AppContext) extends LazyLogging {

  import StoryBuilder._

  private var sourceTaskBuilders: Map[String, ITaskBuilder[ISourceTask]]       = Map.empty
  private var sinkTaskBuilders: Map[String, ITaskBuilder[ISinkTask]]           = Map.empty
  private var transformTaskBuilders: Map[String, ITaskBuilder[ITransformTask]] = Map.empty
  private var fallbackTaskBuilders: Map[String, ITaskBuilder[IFallbackTask]]   = Map.empty

  def addTaskBuilder[T <: ITask: TypeTag](builderClassName: String): Unit =
    addTaskBuilder(Class.forName(builderClassName).asInstanceOf[Class[ITaskBuilder[T]]])

  def addTaskBuilder[T <: ITask: TypeTag](builderClass: Class[ITaskBuilder[T]]): Unit =
    addTaskBuilder(createTaskBuilderInstance(builderClass))

  private def createTaskBuilderInstance[T <: ITask](builderClass: Class[ITaskBuilder[T]]): ITaskBuilder[T] =
    builderClass.newInstance()

  def addTaskBuilder[T <: ITask: TypeTag](builder: ITaskBuilder[T]): Unit = typeOf[T] match {
    case t if t =:= typeOf[ISourceTask] =>
      sourceTaskBuilders += (builder.taskType -> builder.asInstanceOf[ITaskBuilder[ISourceTask]])
    case t if t =:= typeOf[ISinkTask] =>
      sinkTaskBuilders += (builder.taskType -> builder.asInstanceOf[ITaskBuilder[ISinkTask]])
    case t if t =:= typeOf[ITransformTask] =>
      transformTaskBuilders += (builder.taskType -> builder.asInstanceOf[ITaskBuilder[ITransformTask]])
    case t if t =:= typeOf[IFallbackTask] =>
      fallbackTaskBuilders += (builder.taskType -> builder.asInstanceOf[ITaskBuilder[IFallbackTask]])
  }

  def buildStory(info: StoryInfo): Story =
    try {
      new Story(
        StorySettings(info.name, StoryStatus(info.status)),
        buildSourceTask(info.source),
        buildSinkTask(info.sink),
        info.transforms.map(_.split(Regex.quote(TASK_DELIMITER)).map(buildTransformTask).toList),
        info.fallbacks.map(_.split(Regex.quote(TASK_DELIMITER)).map(buildFallbackTask).toList)
      )
    } catch {
      case ex: Throwable =>
        logger.error(s"story ${info.name} build failed with error $ex")
        throw ex
    }

  def buildSourceTask(content: String): ISourceTask = {
    val (taskType, configString) = parseTaskContent(content)
    sourceTaskBuilders.get(taskType).map(buildTask(configString)).get
  }

  def buildTransformTask(content: String): ITransformTask = {
    val (taskType, configString) = parseTaskContent(content)
    transformTaskBuilders.get(taskType).map(buildTask(configString)).get
  }

  def buildSinkTask(content: String): ISinkTask = {
    val (taskType, configString) = parseTaskContent(content)
    sinkTaskBuilders.get(taskType).map(buildTask(configString)).get
  }

  def buildFallbackTask(content: String): IFallbackTask = {
    val (taskType, configString) = parseTaskContent(content)
    fallbackTaskBuilders.get(taskType).map(buildTask(configString)).get
  }

  def parseTaskContent(content: String): (String, String) = {
    val re = content.split(Regex.quote(CONTENT_DELIMITER), 2)
    (re(0), if (re.length == 2) re(1) else "{}")
  }

  def buildTask[T <: ITask](
      configString: String
  )(taskBuilder: ITaskBuilder[T]): T = {
    val config = Util.convertJsonStringToConfig(configString).withFallback(taskBuilder.defaultConfig)
    taskBuilder.buildTask(config)
  }
}

object StoryBuilder {
  val CONTENT_DELIMITER = """#"""
  val TASK_DELIMITER    = """|||"""

  case class StoryInfo(
      name: String,
      status: String,
      settings: String,
      source: String,
      sink: String,
      transforms: Option[String],
      fallbacks: Option[String]
  )
}
