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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream._
import akka.stream.scaladsl.{Flow, RestartFlow}
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.helper.ConfigStringParser
import com.thenetcircle.event_bus.interfaces.EventStatus.{Norm, ToFB}
import com.thenetcircle.event_bus.interfaces.{Event, SinkTask, SinkTaskBuilder}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class HttpSinkSettings(defaultRequest: HttpRequest,
                            expectedResponseBody: String,
                            maxRetryTimes: Int = 9,
                            maxConcurrentRetries: Int = 1,
                            totalRetryTimeout: FiniteDuration = 6.seconds,
                            poolSettings: Option[ConnectionPoolSettings] = None)

class HttpSink(val settings: HttpSinkSettings) extends SinkTask with StrictLogging {

  def createRequest(event: Event): HttpRequest = {
    settings.defaultRequest.withEntity(HttpEntity(event.body.data))
  }

  override def getHandler()(
      implicit runningContext: TaskRunningContext
  ): Flow[Event, (Status, Event), NotUsed] = {

    implicit val system: ActorSystem = runningContext.getActorSystem()
    implicit val materializer: Materializer = runningContext.getMaterializer()
    implicit val exectionContext: ExecutionContext = runningContext.getExecutionContext()

    val poolSettings = settings.poolSettings.getOrElse(ConnectionPoolSettings(system))
    val sendingFlow = Http().superPool[Event](settings = poolSettings)

    val retrySendingFlow: Flow[(HttpRequest, Event), (Status, Event), NotUsed] =
      RestartFlow.withBackoff(minBackoff = 1.second, maxBackoff = 10.minutes, randomFactor = 0.2) {
        () =>
          {
            Flow[(HttpRequest, Event)]
              .via(sendingFlow)
              .mapAsync(1) {
                case (Success(resp), event) =>
                  Unmarshaller
                    .byteStringUnmarshaller(resp.entity)
                    .map { _body =>
                      val body = _body.utf8String

                      logger.debug(s"Got response, status ${resp.status.value}, body: $body")

                      if (resp.status.isSuccess()) {
                        if (body != settings.expectedResponseBody)
                          ToFB() -> event
                        else
                          Norm -> event
                      } else {
                        throw new RuntimeException("Got Non-2xx status code from endpoint")
                      }
                    }

                case (Failure(ex), _) =>
                  logger.debug(s"Got failed with $ex")
                  // let it retry with failed cases (network issue, no response etc...)
                  Future.failed(ex)
              }
          }
      }

    Flow[Event]
      .map(e => createRequest(e) -> e)
      .via(retrySendingFlow)

  }
}

class HttpSinkBuilder() extends SinkTaskBuilder with StrictLogging {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): HttpSink = {

    val config: Config =
      ConfigStringParser
        .convertStringToConfig(configString)
        .withFallback(buildingContext.getSystemConfig().getConfig("task.http-sink"))

    try {
      val requestMethod = config.as[String]("request.method").toUpperCase match {
        case "POST" => HttpMethods.POST
        case "GET"  => HttpMethods.GET
        case unacceptedMethod =>
          throw new IllegalArgumentException(s"Request method $unacceptedMethod is not supported.")
      }
      val requsetUri = Uri(config.as[String]("request.uri"))
      val defaultRequest: HttpRequest = HttpRequest(method = requestMethod, uri = requsetUri)

      val poolSettingsMap = config.as[Map[String, String]]("pool")
      val poolSettingsOption = if (poolSettingsMap.nonEmpty) {
        var _settingsStr =
          poolSettingsMap.foldLeft("")((acc, kv) => acc + "\n" + s"${kv._1} = ${kv._2}")
        Some(ConnectionPoolSettings(s"""akka.http.host-connection-pool {
             |${_settingsStr}
             |}""".stripMargin))
      } else None

      val settings = HttpSinkSettings(
        defaultRequest,
        config.as[String]("expected-response"),
        config.as[Int]("max-retry-times"),
        config.as[Int]("max-concurrent-retries"),
        config.as[FiniteDuration]("total-retry-timeout"),
        poolSettingsOption
      )

      new HttpSink(settings)

    } catch {
      case ex: Throwable =>
        logger.error(s"Build HttpSink failed with error: ${ex.getMessage}")
        throw ex
    }

  }
}
