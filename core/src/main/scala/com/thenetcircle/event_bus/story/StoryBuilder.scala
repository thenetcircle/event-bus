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

import com.thenetcircle.event_bus.context.{AppContext, TaskBuildingContext}
import com.thenetcircle.event_bus.story.interfaces._
import com.typesafe.scalalogging.LazyLogging

import scala.reflect.runtime.universe._
import scala.util.matching.Regex

class StoryBuilder()(
    implicit appContext: AppContext
) extends LazyLogging {

  implicit val _context: TaskBuildingContext = new TaskBuildingContext(appContext)

  import StoryBuilder._

  private var sourceTaskBuilders: Map[String, ITaskBuilder[ISourceTask]]       = Map.empty
  private var sinkTaskBuilders: Map[String, ITaskBuilder[ISinkTask]]           = Map.empty
  private var transformTaskBuilders: Map[String, ITaskBuilder[ITransformTask]] = Map.empty
  private var fallbackTaskBuilders: Map[String, ITaskBuilder[IFallbackTask]]   = Map.empty

  def addTaskBuilder(builderClassName: String): Unit =
    addTaskBuilder(Class.forName(builderClassName).asInstanceOf[Class[ITaskBuilder[ITask]]])

  def addTaskBuilder(builderClass: Class[ITaskBuilder[ITask]]): Unit =
    addTaskBuilder(createTaskBuilderInstance(builderClass))

  private def createTaskBuilderInstance(builderClass: Class[ITaskBuilder[ITask]]): ITaskBuilder[ITask] =
    builderClass.newInstance()

  def addTaskBuilder[T <: ITask: TypeTag](builder: ITaskBuilder[T]): Unit = builder match {
    case _ if typeOf[T] <:< typeOf[ISourceTask]    => sourceTaskBuilders += (builder.taskType    -> builder)
    case _ if typeOf[T] <:< typeOf[ISinkTask]      => sinkTaskBuilders += (builder.taskType      -> builder)
    case _ if typeOf[T] <:< typeOf[ITransformTask] => transformTaskBuilders += (builder.taskType -> builder)
    case _ if typeOf[T] <:< typeOf[IFallbackTask]  => fallbackTaskBuilders += (builder.taskType  -> builder)
  }

  def buildStory(rawData: StoryRawData): Story =
    try {
      new Story(
        StorySettings(rawData.name, StoryStatus(rawData.status)),
        buildSourceTask(rawData.source),
        buildSinkTask(rawData.sink),
        rawData.transforms.map(_.split(Regex.quote(TASK_DELIMITER)).map(buildTransformTask).toList),
        rawData.fallback.map(_.split(Regex.quote(TASK_DELIMITER)).map(buildFallbackTask).toList)
      )
    } catch {
      case ex: Throwable =>
        logger.error(s"story ${rawData.name} build failed with error $ex")
        throw ex
    }

  def parseTaskContent(content: String): (String, String) = {
    val re = content.split(Regex.quote(CONTENT_DELIMITER), 2)
    (re(0), if (re.length == 2) re(1) else "{}")
  }

  def buildSourceTask(content: String): ISourceTask = {
    val (taskType, configString) = parseTaskContent(content)
    sourceTaskBuilders.get(taskType).map(_.build(configString)).get
  }

  def buildTransformTask(content: String): ITransformTask = {
    val (taskType, configString) = parseTaskContent(content)
    transformTaskBuilders.get(taskType).map(_.build(configString)).get
  }

  def buildSinkTask(content: String): ISinkTask = {
    val (taskType, configString) = parseTaskContent(content)
    sinkTaskBuilders.get(taskType).map(_.build(configString)).get
  }

  def buildFallbackTask(content: String): IFallbackTask = {
    val (taskType, configString) = parseTaskContent(content)
    fallbackTaskBuilders.get(taskType).map(_.build(configString)).get
  }
}

object StoryBuilder {
  val CONTENT_DELIMITER = """#"""
  val TASK_DELIMITER    = """|||"""

  case class StoryRawData(
      name: String,
      status: String,
      settings: String,
      source: String,
      sink: String,
      transforms: Option[String],
      fallback: Option[String]
  )
}
