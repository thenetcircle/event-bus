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

import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import akka.http.scaladsl.model._
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.story.interfaces.ITaskBuilder
import com.thenetcircle.event_bus.{AppContext, BuildInfo}
import com.typesafe.config.{Config, ConfigFactory}

case class FirebaseSinkSettings(
    httpSinkSettings: HttpSinkSettings
)

class FirebaseSink(val fbSinkSettings: FirebaseSinkSettings) extends HttpSink(fbSinkSettings.httpSinkSettings) {

  override def createHttpRequest(event: Event): HttpRequest = {
    if (!event.hasExtra("objectContent")) {
      throw new IllegalStateException(s"""A event sending to Firebase has not "objectContent", ${event.summary}""")
    }

    val requestContentType = ContentTypes.`application/json`
    val requestData        = event.getExtra("objectContent").get

    taskLogger.debug(s"created request data: $requestData")

    settings.defaultRequest.withEntity(HttpEntity(requestContentType, requestData))
  }

  override protected def getEventSummary(event: Event): String = event.summaryWithContent

}

class FirebaseSinkBuilder() extends HttpSinkBuilder with ITaskBuilder[FirebaseSink] {

  override val taskType: String = "firebase"

  override def defaultConfig: Config =
    ConfigFactory
      .parseString(
        s"""{
        |  # this could be overwritten by event info later
        |  default-request {
        |    # uri = "https://fcm.googleapis.com/fcm/send"
        |    # auth-key = ""
        |    method = POST
        |    protocol = "HTTP/1.1"
        |    headers {
        |      "user-agent": "event-bus/${BuildInfo.version}"
        |    }
        |  }
        |
        |  # set this to be empty, so only check response code
        |  expected-response = ""
        |  allow-extra-signals = false
        |
        |  use-https-connection-pool = true
        |  log-request-body = true
        |
        |  pool {
        |    max-connections = 4
        |    min-connections = 0
        |    max-open-requests = 32
        |    pipelining-limit = 1
        |    idle-timeout = 30 s
        |  }
        |}""".stripMargin
      )
      .withFallback(super[HttpSinkBuilder].defaultConfig)

  override protected def buildHttpRequestFromConfig(config: Config)(implicit appContext: AppContext): HttpRequest = {
    val request    = super.buildHttpRequestFromConfig(config)
    val authHeader = HttpHeader.parse("Authorization", s"key=${config.getString("auth-key")}")
    authHeader match {
      case Ok(header, errors) => request.addHeader(header)
      case _                  => throw new IllegalArgumentException("Parsing Authorization header failed.")
    }
  }

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): FirebaseSink =
    try {
      require(config.hasPath("default-request.auth-key"), "Authorization Key is required.")

      val fbSinkSettings = FirebaseSinkSettings(httpSinkSettings = createHttpSinkSettings(config))
      new FirebaseSink(fbSinkSettings)
    } catch {
      case ex: Throwable =>
        logger.error(s"Build FirebaseSink failed with error: ${ex.getMessage}")
        throw ex
    }
}
