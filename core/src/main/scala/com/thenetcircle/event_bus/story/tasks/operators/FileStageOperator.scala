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

package com.thenetcircle.event_bus.story.tasks.operators

import java.nio.file.{Path, Paths, StandardOpenOption}

import akka.NotUsed
import akka.stream.scaladsl.{FileIO, Flow}
import akka.util.ByteString
import com.thenetcircle.event_bus.AppContext
import com.thenetcircle.event_bus.event.EventStatus.{STAGED, STAGING}
import com.thenetcircle.event_bus.misc.Logging
import com.thenetcircle.event_bus.story.interfaces.{IFailoverTask, ITaskBuilder, IUndiOperator}
import com.thenetcircle.event_bus.story.{Payload, StoryMat, TaskRunningContext}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

import scala.util.matching.Regex

case class FileStageSettings(
    path: String,
    contentDelimiter: String = "<tab>",
    eventDelimiter: String = "<newline>#-:#:-#<newline>"
)

class FileStageOperator(val settings: FileStageSettings) extends IUndiOperator with IFailoverTask with Logging {
  private def getFilePath()(
      implicit runningContext: TaskRunningContext
  ): Path =
    Paths.get(replaceSubstitutes(settings.path))

  private def replaceSubstitutes(path: String)(
      implicit runningContext: TaskRunningContext
  ): String =
    path
      .replaceAll(Regex.quote("""{app_name}"""), runningContext.getAppContext().getAppName())
      .replaceAll(Regex.quote("""{app_env}"""), runningContext.getAppContext().getAppEnv())
      .replaceAll(Regex.quote("""{story_name}"""), getStoryName())
      .replaceAll(Regex.quote("""{task_name}"""), getTaskName())

  private def replaceDelimiter(delimiter: String): String =
    delimiter
      .replaceAll(Regex.quote("<tab>"), "\t")
      .replaceAll(Regex.quote("<newline>"), "\n")

  override def flow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat] = {
    val contentDelimiter = replaceDelimiter(settings.contentDelimiter)
    val eventDelimiter   = replaceDelimiter(settings.eventDelimiter)

    Flow[Payload]
      .alsoTo(
        Flow[Payload]
          .collect {
            case (STAGING(cause, taskName), event) =>
              val causeString = cause.map(_.getClass.getName).getOrElse("unknown")
              ByteString(s"$taskName$contentDelimiter$causeString$contentDelimiter${event.body.data}$eventDelimiter")
          }
          .to(FileIO.toPath(getFilePath(), Set(StandardOpenOption.APPEND, StandardOpenOption.CREATE)))
      )
      .map {
        case (_: STAGING, event) => (STAGED, event)
        case others              => others
      }
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {}
}

class FileStageOperatorBuilder() extends ITaskBuilder[FileStageOperator] {

  override val taskType: String = "file-stage"

  override val defaultConfig: Config = ConfigFactory.empty()

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): FileStageOperator =
    new FileStageOperator(FileStageSettings(path = config.as[String]("path")))

}
