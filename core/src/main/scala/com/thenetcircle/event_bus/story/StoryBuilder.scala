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
import com.thenetcircle.event_bus.story.Story.OpExecPos
import com.thenetcircle.event_bus.story.interfaces._
import com.typesafe.scalalogging.LazyLogging

import scala.reflect.runtime.universe._
import scala.util.control.NonFatal
import scala.util.matching.Regex

class StoryBuilder()(implicit appContext: AppContext) extends LazyLogging {

  import StoryBuilder._

  private var sourceBuilders: Map[String, ITaskBuilder[ISource]]     = Map.empty
  private var sinkBuilders: Map[String, ITaskBuilder[ISink]]         = Map.empty
  private var operatorBuilders: Map[String, ITaskBuilder[IOperator]] = Map.empty

  def addTaskBuilder[T <: ITask: TypeTag](builderClassName: String): Unit =
    addTaskBuilder(Class.forName(builderClassName).asInstanceOf[Class[ITaskBuilder[T]]])

  def addTaskBuilder[T <: ITask: TypeTag](builderClass: Class[ITaskBuilder[T]]): Unit =
    addTaskBuilder(createTaskBuilderInstance(builderClass))

  private def createTaskBuilderInstance[T <: ITask](builderClass: Class[ITaskBuilder[T]]): ITaskBuilder[T] =
    builderClass.newInstance()

  def addTaskBuilder[T <: ITask: TypeTag](builder: ITaskBuilder[T]): Unit = {
    typeOf[T] match {
      case t if t <:< typeOf[ISource] =>
        sourceBuilders += (builder.taskType -> builder.asInstanceOf[ITaskBuilder[ISource]])
      case t if t <:< typeOf[ISink] =>
        sinkBuilders += (builder.taskType -> builder.asInstanceOf[ITaskBuilder[ISink]])
      case t if t <:< typeOf[IOperator] =>
        operatorBuilders += (builder.taskType -> builder.asInstanceOf[ITaskBuilder[IOperator]])
    }
    builder.setStoryBuilder(this)
  }

  def buildStory(info: StoryInfo): Story =
    try {
      new Story(
        StorySettings(info.name, info.settings),
        buildSource(info.source),
        buildSink(info.sink),
        info.operators.map(_.split(Regex.quote(TASK_DELIMITER)).map(buildOperator).toList)
      )
    } catch {
      case ex: Throwable =>
        logger.error(s"Buiding story failed with StoryInfo: $info and error: $ex")
        throw ex
    }

  def buildSource(content: String): ISource = {
    val taskContent = parseTaskContent(content)
    sourceBuilders.get(taskContent.metadata.taskType).map(buildTaskWithBuilder(taskContent.config)).get
  }

  def buildSink(content: String): ISink = {
    val taskContent = parseTaskContent(content)
    sinkBuilders.get(taskContent.metadata.taskType).map(buildTaskWithBuilder(taskContent.config)).get
  }

  def buildOperator(content: String): (OpExecPos, IOperator) = {
    val taskContent = parseTaskContent(content)
    (
      OpExecPos(taskContent.metadata.extra),
      operatorBuilders.get(taskContent.metadata.taskType).map(buildTaskWithBuilder(taskContent.config)).get
    )
  }

  def buildFailoverTask(content: String): IFailoverTask = {
    val taskContent  = parseTaskContent(content)
    val taskCategory = taskContent.metadata.extra.getOrElse("operator")
    try {
      taskCategory.toLowerCase match {
        case "operator" => buildOperator(content)._2.asInstanceOf[IFailoverTask]
        case "sink"     => buildSink(content).asInstanceOf[IFailoverTask]
        case _          => throw new IllegalArgumentException(s"Unsupported task category $taskCategory")
      }
    } catch {
      case NonFatal(ex) =>
        val errorMsg = s"Building failover task failed with task content: $taskContent"
        logger.error(errorMsg, ex)
        throw new IllegalArgumentException(errorMsg, ex)
    }
  }

  def parseTaskContent(content: String): TaskContent = {
    val re     = content.split(Regex.quote(CONTENT_DELIMITER), 2)
    val metaRe = re(0).split(Regex.quote(CONTENT_METADATA_DELIMITER))
    TaskContent(
      metadata = TaskContentMetadata(
        taskType = metaRe(0),
        extra = if (metaRe.length > 1) Some(metaRe(1)) else None,
      ),
      config = if (re.length == 2) re(1) else "{}"
    )
  }

  def buildTaskWithBuilder[T <: ITask](
      configString: String
  )(taskBuilder: ITaskBuilder[T]): T = {
    val config = Util.convertJsonStringToConfig(configString).withFallback(taskBuilder.defaultConfig)
    taskBuilder.buildTask(config)
  }
}

object StoryBuilder {
  val CONTENT_DELIMITER          = """#"""
  val CONTENT_METADATA_DELIMITER = """:"""
  val TASK_DELIMITER             = """|||"""

  case class StoryInfo(
      name: String,
      settings: String,
      source: String,
      sink: String,
      operators: Option[String]
  )

  case class TaskContent(
      metadata: TaskContentMetadata,
      config: String
  )

  case class TaskContentMetadata(
      taskType: String,
      extra: Option[String] = None
  )
}
