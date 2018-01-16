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

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ServerSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.thenetcircle.event_bus.event.extractor.{
  DataFormat,
  EventExtractor,
  EventExtractorFactory
}
import com.thenetcircle.event_bus.interface.{SourceTask, SourceTaskBuilder}
import com.thenetcircle.event_bus.misc.ConfigStringParser
import com.thenetcircle.event_bus.story.TaskRunningContext
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class HttpSourceSettings(interface: String = "0.0.0.0",
                              port: Int = 8000,
                              format: DataFormat = DataFormat("ActivityStreams"),
                              maxConnections: Int = 1024,
                              succeededResponse: String = "ok",
                              requestTimeout: String = "10 s",
                              idleTimeout: String = "60 s",
                              bindTimeout: String = "1s",
                              lingerTimeout: String = "1 min")

class HttpSource(val settings: HttpSourceSettings) extends SourceTask with StrictLogging {

  def getSucceededResponse(event: Event): HttpResponse = {
    HttpResponse(entity = HttpEntity(settings.succeededResponse))
  }

  def getInternalHandler(handler: Flow[Event, (Try[Done], Event), NotUsed])(
      implicit materializer: Materializer,
      executionContext: ExecutionContext
  ): Flow[HttpRequest, HttpResponse, NotUsed] = {
    val extractor: EventExtractor = EventExtractorFactory.getExtractor(settings.format)
    val unmarshaller: Unmarshaller[HttpEntity, Event] =
      Unmarshaller.byteStringUnmarshaller.andThen(
        Unmarshaller.apply(_ => data => extractor.extract(data))
      )

    // TODO: test failed response
    Flow[HttpRequest]
      .mapAsync(1)(request => unmarshaller.apply(request.entity))
      .via(handler)
      .map {
        case (Success(_), event) => getSucceededResponse(event)
        case (Failure(ex), _)    => HttpResponse(entity = HttpEntity(ex.getMessage))
      }
  }

  def getServerSettings(): ServerSettings = ServerSettings(s"""
                      |akka.http.server {
                      |  idle-timeout = ${settings.idleTimeout}
                      |  request-timeout = ${settings.requestTimeout}
                      |  bind-timeout = ${settings.bindTimeout}
                      |  linger-timeout = ${settings.lingerTimeout}
                      |  max-connections = ${settings.maxConnections}
                      |}
      """.stripMargin)

  override def runWith(
      handler: Flow[Event, (Try[Done], Event), NotUsed]
  )(implicit context: TaskRunningContext): (KillSwitch, Future[Done]) = {
    implicit val materializer: Materializer = context.getMaterializer()
    implicit val executionContext: ExecutionContext = context.getExecutionContext()

    val sharedKillSwitch = KillSwitches.shared("http-source-shard-killswitch")

    val internalNandler =
      Flow[HttpRequest].via(sharedKillSwitch.flow).via(getInternalHandler(handler))

    val httpBindFuture =
      Http().bindAndHandle(
        handler = internalNandler,
        interface = settings.interface,
        settings = getServerSettings()
      )

    httpBindFuture.onComplete {
      case Success(binding) => binding.unbind()
      case Failure(ex) =>
        logger.error(s"HttpBindFuture failed with error $ex")
    }

    (sharedKillSwitch, httpBindFuture.map(_ => Done))
  }

}

class HttpSourceBuilder() extends SourceTaskBuilder with StrictLogging {

  override def build(configString: String): HttpSource = {

    try {

      val defaultConfig: Config =
        ConfigStringParser.convertStringToConfig("""
          |{
          |  "interface": "0.0.0.0",
          |  "port": 8000,
          |  "format": "ActivityStreams",
          |  "max-connections": 1024,
          |  "succeeded-response": "ok",
          |  "request-timeout": "10 s",
          |  "idle-timeout": "60 s",
          |  "bind-timeout": "1s",
          |  "linger-timeout": "1 min",
          |}""".stripMargin)

      val config: Config =
        ConfigStringParser.convertStringToConfig(configString).withFallback(defaultConfig)

      val settings = HttpSourceSettings(
        config.as[String]("interface"),
        config.as[Int]("port"),
        config.as[DataFormat]("format"),
        config.as[Int]("max-connections"),
        config.as[String]("succeeded-response"),
        config.as[String]("request-timeout"),
        config.as[String]("idle-timeout"),
        config.as[String]("bind-timeout"),
        config.as[String]("linger-timeout")
      )

      new HttpSource(settings)

    } catch {
      case ex: Throwable =>
        logger.error(s"Build HttpSource failed with error: $ex")
        throw ex
    }
  }
}
