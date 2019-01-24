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

import java.nio.file.{OpenOption, Paths, StandardOpenOption}
import java.time.LocalDateTime

import akka.stream.alpakka.file.scaladsl.LogRotatorSink
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.ByteString
import com.thenetcircle.event_bus.AppContext
import com.thenetcircle.event_bus.event.EventStatus.{STAGED, STAGING}
import com.thenetcircle.event_bus.misc.Util
import com.thenetcircle.event_bus.story.interfaces.{IFailoverTask, ITaskBuilder, ITaskLogging, IUndiOperator}
import com.thenetcircle.event_bus.story.{Payload, StoryMat, TaskRunningContext}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

import scala.util.matching.Regex

case class FileOperatorSettings(
    path: String,
    lineDelimiter: String = "<tab>",
    eventDelimiter: String = "<newline>#-:#:-#<newline>"
)

class FileOperator(val settings: FileOperatorSettings) extends IUndiOperator with IFailoverTask with ITaskLogging {
  private def getBaseFilePath()(
      implicit runningContext: TaskRunningContext
  ): String =
    replaceSubstitutes(settings.path)

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
    val lineDelimiter  = replaceDelimiter(settings.lineDelimiter)
    val eventDelimiter = replaceDelimiter(settings.eventDelimiter)
    val baseFilePath   = getBaseFilePath()
    def getFilePath(): String = {
      val currentDateTime = LocalDateTime.now()
      baseFilePath
        .replaceAll(Regex.quote("{year}"), currentDateTime.getYear.toString)
        .replaceAll(Regex.quote("{month}"), "%02d".format(currentDateTime.getMonthValue))
        .replaceAll(Regex.quote("{day}"), "%02d".format(currentDateTime.getDayOfMonth))
        .replaceAll(Regex.quote("{minute}"), "%02d".format(currentDateTime.getMinute))
    }

    val fileOpenOptions: Set[OpenOption] = Set(StandardOpenOption.APPEND, StandardOpenOption.CREATE)
    // val normalFileSink: Sink[ByteString, Any] = FileIO.toPath(Paths.get(getFilePath()), fileOpenOptions)
    val rotationFunction = () => {
      var currentFilepath: Option[String] = None
      (_: ByteString) =>
        {
          val newFilePath = getFilePath()
          if (currentFilepath.contains(newFilePath)) {
            None
          } else {
            currentFilepath = Some(newFilePath)
            Some(Paths.get(newFilePath))
          }
        }
    }
    val rotatedFileSink: Sink[ByteString, Any] = LogRotatorSink(rotationFunction, fileOpenOptions)

    Flow[Payload]
      .alsoTo(
        Flow[Payload]
          .collect {
            case (STAGING(cause, taskName), event) =>
              taskLogger
                .info(
                  s"Going to send a STAGING event [${Util
                    .getBriefOfEvent(event)}] to the failover file [${getFilePath()}]"
                )
              val causeString = cause.map(_.getClass.getName).getOrElse("unknown")
              ByteString(s"$taskName$lineDelimiter$causeString$lineDelimiter${event.body.data}$eventDelimiter")
          }
          .to(rotatedFileSink)
      )
      .map {
        case (_: STAGING, event) => (STAGED, event)
        case others              => others
      }
  }

  override def failoverFlow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat] = flow

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {}
}

class FileOperatorBuilder extends ITaskBuilder[FileOperator] {

  override val taskType: String = "file"

  override val defaultConfig: Config =
    ConfigFactory.parseString(
      """{
        |  line-delimiter = "<tab>"
        |  event-delimiter = "<newline>#-:#:-#<newline>"
        |}""".stripMargin
    )

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): FileOperator = {
    val settings = FileOperatorSettings(
      path = config.as[String]("path"),
      lineDelimiter = config.as[String]("line-delimiter"),
      eventDelimiter = config.as[String]("event-delimiter")
    )
    new FileOperator(settings)
  }

}
