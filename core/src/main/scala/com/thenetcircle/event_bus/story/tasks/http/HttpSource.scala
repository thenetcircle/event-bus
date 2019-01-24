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

package com.thenetcircle.event_bus.story.tasks.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.settings.ServerSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.AppContext
import com.thenetcircle.event_bus.event.EventStatus.{FAILED, NORMAL, STAGING, SuccStatus}
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.thenetcircle.event_bus.event.extractor.{DataFormat, EventExtractingException, EventExtractorFactory}
import com.thenetcircle.event_bus.event.{Event, EventStatus}
import com.thenetcircle.event_bus.misc.Logging
import com.thenetcircle.event_bus.story.interfaces.{ISource, ITaskBuilder, ITaskLogging}
import com.thenetcircle.event_bus.story.{Payload, StoryMat, TaskRunningContext}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.Success

case class HttpSourceSettings(
    interface: String = "0.0.0.0",
    port: Int = 8000,
    format: DataFormat = DataFormat("ActivityStreams"),
    succeededResponse: String = "ok",
    serverSettings: Option[ServerSettings] = None
)

class HttpSource(val settings: HttpSourceSettings) extends ISource with ITaskLogging {

  def createResponse(result: Payload): HttpResponse =
    result match {
      case (_: SuccStatus, _) =>
        HttpResponse(entity = HttpEntity(settings.succeededResponse))
      case (STAGING(optionEx, _), _) =>
        HttpResponse(
          status = StatusCodes.InternalServerError,
          entity = HttpEntity(optionEx.map(_.getMessage).getOrElse("Unhandled ToFallBack Status"))
        )
      case (FAILED(ex: EventExtractingException, _), _) =>
        HttpResponse(
          status = StatusCodes.BadRequest,
          entity = HttpEntity(ex.getMessage)
        )
      case (FAILED(ex, _), _) =>
        HttpResponse(
          status = StatusCodes.InternalServerError,
          entity = HttpEntity(ex.getMessage)
        )
    }

  def getRequestUnmarshallerHandler()(
      implicit materializer: Materializer,
      executionContext: ExecutionContext
  ): Flow[HttpRequest, Payload, NotUsed] = {
    val unmarshaller: Unmarshaller[HttpEntity, Event] =
      EventExtractorFactory.getHttpEntityUnmarshaller(settings.format)

    Flow[HttpRequest]
      .mapAsync(1)(request => {
        unmarshaller(request.entity)
          .map[(EventStatus, Event)](event => {
            taskLogger.info(s"Received a new event: " + event.summary)
            taskLogger.debug(s"Extracted content of the event: $event")
            (NORMAL, event)
          })
          .recover {
            case ex: EventExtractingException =>
              taskLogger.warn(s"Extract event from a http request failed with error $ex")
              (FAILED(ex, getTaskName()), Event.fromException(ex))
          }
      })
  }

  var killSwitchOption: Option[KillSwitch] = None

  override def run(
      storyFlow: Flow[Payload, Payload, StoryMat]
  )(implicit runningContext: TaskRunningContext): Future[Done] = {
    implicit val system: ActorSystem                = runningContext.getActorSystem()
    implicit val materializer: Materializer         = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    // TODO consider merge sub-streams, since request sub-stream will materialize subsequent tasks
    //      for example Decoupler buffer will not work on short-term connections
    //      for now is ok since we are using Nginx as an reverse proxy
    val internalHandler =
      Flow[HttpRequest]
        .via(getRequestUnmarshallerHandler())
        .via(storyFlow)
        .map(createResponse)

    val httpBindFuture =
      Http().bindAndHandle(
        handler = internalHandler,
        interface = settings.interface,
        port = settings.port,
        settings = settings.serverSettings.getOrElse(ServerSettings(system))
      )

    val donePromise = Promise[Done]()

    killSwitchOption = Some(new KillSwitch {
      override def abort(ex: Throwable): Unit = shutdown()
      override def shutdown(): Unit = {
        taskLogger.info(s"Unbinding HTTP port.")
        Await.ready(
          httpBindFuture.flatMap(_.unbind().map(_ => donePromise tryComplete Success(Done))),
          5.seconds
        )
      }
    })

    donePromise.future
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    taskLogger.info(s"Shutting down HTTP Source")
    killSwitchOption.foreach(k => {
      k.shutdown(); killSwitchOption = None
    })
  }
}

class HttpSourceBuilder() extends ITaskBuilder[HttpSource] with Logging {

  override val taskType: String = "http"

  override val defaultConfig: Config =
    ConfigFactory.parseString(
      """{
        |  interface = "0.0.0.0"
        |  port = 8000
        |  format = ActivityStreams
        |  succeeded-response = ok
        |
        |  # server settings will override the default settings of akka.http.server
        |  server {
        |    # max-connections = 1024
        |    # ...
        |  }
        |}""".stripMargin
    )

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): HttpSource =
    try {
      val serverSettingsMap = config.as[Map[String, String]]("server")
      val serverSettings = if (serverSettingsMap.nonEmpty) {
        var settingsStr =
          serverSettingsMap.foldLeft("")((acc, kv) => acc + "\n" + s"${kv._1} = ${kv._2}")
        Some(ServerSettings(s"""akka.http.server {
                          |$settingsStr
                          |}""".stripMargin))
      } else None

      val settings = HttpSourceSettings(
        config.as[String]("interface"),
        config.as[Int]("port"),
        config.as[DataFormat]("format"),
        config.as[String]("succeeded-response"),
        serverSettings
      )

      new HttpSource(settings)
    } catch {
      case ex: Throwable =>
        logger.error(s"Build HttpSource failed with error: $ex")
        throw ex
    }
}
