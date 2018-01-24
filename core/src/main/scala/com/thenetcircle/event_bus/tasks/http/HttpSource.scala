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
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ServerSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.event.EventImpl
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.thenetcircle.event_bus.event.extractor.{
  DataFormat,
  EventExtractingException,
  EventExtractorFactory
}
import com.thenetcircle.event_bus.misc.Util
import com.thenetcircle.event_bus.interfaces.EventStatus.{Fail, Norm, Succ}
import com.thenetcircle.event_bus.interfaces.{Event, EventStatus, SourceTask, SourceTaskBuilder}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.Success

case class HttpSourceSettings(interface: String = "0.0.0.0",
                              port: Int = 8000,
                              format: DataFormat = DataFormat("ActivityStreams"),
                              succeededResponse: String = "ok",
                              serverSettings: ServerSettings)

class HttpSource(val settings: HttpSourceSettings) extends SourceTask with StrictLogging {

  def createResponse(result: (EventStatus, Event)): HttpResponse = result match {
    case (_: Succ, _) =>
      HttpResponse(entity = HttpEntity(settings.succeededResponse))
    case (Fail(ex), _) =>
      HttpResponse(
        entity = HttpEntity(s"The request was processing failed with error ${ex.getMessage}.")
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
          .map[(EventStatus, Event)](event => (Norm, event))
          .recover {
            case ex: EventExtractingException =>
              logger.debug(s"A http request unmarshaller failed with error $ex")
              (Fail(ex), EventImpl.createFromFailure(ex))
          }
      })
  }

  var killSwitchOption: Option[KillSwitch] = None

  override def runWith(
      handler: Flow[(EventStatus, Event), (EventStatus, Event), NotUsed]
  )(implicit runningContext: TaskRunningContext): Future[Done] = {
    implicit val system: ActorSystem = runningContext.getActorSystem()
    implicit val materializer: Materializer = runningContext.getMaterializer()
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
        settings = settings.serverSettings
      )

    val donePromise = Promise[Done]()

    killSwitchOption = Some(new KillSwitch {
      override def abort(ex: Throwable): Unit = shutdown()
      override def shutdown(): Unit =
        Await.ready(
          httpBindFuture.flatMap(_.unbind().map(_ => donePromise tryComplete Success(Done))),
          5.seconds
        )
    })

    donePromise.future
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    killSwitchOption.foreach(k => { k.shutdown(); killSwitchOption = None })
  }
}

class HttpSourceBuilder() extends SourceTaskBuilder with StrictLogging {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): HttpSource = {
    try {
      val config: Config =
        Util
          .convertJsonStringToConfig(configString)
          .withFallback(buildingContext.getSystemConfig().getConfig("task.http-source"))

      val serverSettingsMap = config.as[Map[String, String]]("server")
      val serverSettings = {
        var _settingsStr =
          serverSettingsMap.foldLeft("")((acc, kv) => acc + "\n" + s"${kv._1} = ${kv._2}")
        ServerSettings(s"""akka.http.server {
                          |${_settingsStr}
                          |}""".stripMargin)
      }

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
}
