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

package com.thenetcircle.event_bus.tasks.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.settings.ServerSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.event.EventStatus.{FAIL, NORM, SuccStatus, TOFB}
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.thenetcircle.event_bus.event.extractor.{DataFormat, EventExtractingException, EventExtractorFactory}
import com.thenetcircle.event_bus.event.{Event, EventStatus}
import com.thenetcircle.event_bus.interfaces.{SourceTask, SourceTaskBuilder}
import com.thenetcircle.event_bus.misc.{Logging, Util}
import com.typesafe.config.Config
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

class HttpSource(val settings: HttpSourceSettings) extends SourceTask with Logging {

  def createResponse(result: (EventStatus, Event)): HttpResponse =
    result match {
      case (_: SuccStatus, _) =>
        HttpResponse(entity = HttpEntity(settings.succeededResponse))
      case (TOFB(optionEx), _) =>
        HttpResponse(
          status = StatusCodes.InternalServerError,
          entity = HttpEntity(optionEx.map(_.getMessage).getOrElse("Unhandled ToFallBack Status"))
        )
      case (FAIL(ex: EventExtractingException), _) =>
        HttpResponse(
          status = StatusCodes.BadRequest,
          entity = HttpEntity(ex.getMessage)
        )
      case (FAIL(ex), _) =>
        HttpResponse(
          status = StatusCodes.InternalServerError,
          entity = HttpEntity(ex.getMessage)
        )
    }

  def getRequestUnmarshallerHandler()(
      implicit materializer: Materializer,
      executionContext: ExecutionContext
  ): Flow[HttpRequest, (EventStatus, Event), NotUsed] = {
    val unmarshaller: Unmarshaller[HttpEntity, Event] =
      EventExtractorFactory.getHttpEntityUnmarshaller(settings.format)

    Flow[HttpRequest]
      .mapAsync(1)(request => {
        unmarshaller(request.entity)
          .map[(EventStatus, Event)](event => {
            producerLogger.info("received a new event: " + Util.getBriefOfEvent(event))
            producerLogger.debug(s"extracted event content: $event")
            (NORM, event)
          })
          .recover {
            case ex: EventExtractingException =>
              producerLogger.warn(s"extract event from a http request failed with error $ex")
              (FAIL(ex), Event.fromException(ex))
          }
      })
  }

  var killSwitchOption: Option[KillSwitch] = None

  override def runWith(
      handler: Flow[(EventStatus, Event), (EventStatus, Event), NotUsed]
  )(implicit runningContext: TaskRunningContext): Future[Done] = {
    implicit val system: ActorSystem                = runningContext.getActorSystem()
    implicit val materializer: Materializer         = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    val internalHandler =
      Flow[HttpRequest]
        .via(getRequestUnmarshallerHandler())
        .via(handler)
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
        logger.info(s"unbinding http port.")
        Await.ready(
          httpBindFuture.flatMap(_.unbind().map(_ => donePromise tryComplete Success(Done))),
          5.seconds
        )
      }
    })

    donePromise.future
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    logger.info(s"shutting down http-source of story ${runningContext.getStoryName()}.")
    killSwitchOption.foreach(k => {
      k.shutdown(); killSwitchOption = None
    })
  }
}

class HttpSourceBuilder() extends SourceTaskBuilder with Logging {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): HttpSource =
    try {
      val config: Config =
        Util
          .convertJsonStringToConfig(configString)
          .withFallback(buildingContext.getSystemConfig().getConfig("task.http-source"))

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
