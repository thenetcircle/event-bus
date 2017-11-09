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

package com.thenetcircle.event_bus.dispatcher.emitter

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import com.thenetcircle.event_bus.dispatcher.emitter.EmitterType.EmitterType
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

case class HttpEmitterSettings(name: String,
                               host: String,
                               port: Int,
                               maxRetryTimes: Int,
                               // TODO: set pool maxRetries to 1
                               connectionPoolSettings: ConnectionPoolSettings,
                               defaultRequest: HttpRequest,
                               // TODO: set to optional
                               expectedResponse: Option[String] = None)
    extends EmitterSettings {
  override val emitterType: EmitterType = EmitterType.HTTP
}

object HttpEmitterSettings extends StrictLogging {
  def apply(_config: Config)(implicit system: ActorSystem): HttpEmitterSettings = {
    val config: Config =
      _config.withFallback(system.settings.config.getConfig("event-bus.emitter.http"))

    logger.info(s"Creating a new HttpEmitterSettings according to config: $config")

    try {
      val rootConfig =
        system.settings.config
      val connectionPoolSettings: ConnectionPoolSettings =
        if (config.hasPath("akka.http.host-connection-pool")) {
          ConnectionPoolSettings(
            config
              .withFallback(rootConfig)
          )
        } else {
          ConnectionPoolSettings(rootConfig)
        }

      val requestMethod = if (config.hasPath("request.method")) {
        config.getString("request.method").toUpperCase() match {
          case "POST" => HttpMethods.POST
          case "GET"  => HttpMethods.GET
          case unacceptedMethod =>
            throw new IllegalArgumentException(
              s"Http request method $unacceptedMethod is unsupported."
            )
        }
      } else {
        HttpMethods.POST
      }
      val requsetUri =
        if (config.hasPath("request.uri")) Uri(config.getString("request.uri"))
        else Uri./

      val defaultRequest: HttpRequest = HttpRequest(method = requestMethod, uri = requsetUri)

      val expectedResponse =
        if (config.hasPath("expected-response-data"))
          Some(config.getString("expected-response-data"))
        else None

      HttpEmitterSettings(
        config.getString("name"),
        config.getString("request.host"),
        config.getInt("request.port"),
        config.getInt("max-retry-times"),
        connectionPoolSettings,
        defaultRequest,
        expectedResponse
      )
    } catch {
      case ex: Throwable =>
        logger.error(s"Creating HttpEmitterSettings failed with error: ${ex.getMessage}")
        throw ex
    }
  }
}
