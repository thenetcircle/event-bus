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

package com.thenetcircle.event_bus.story.builder

import akka.http.scaladsl.model._
import com.google.common.net.HttpHeaders
import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.story.tasks.http.{HttpSink, HttpSinkBuilder}

import scala.concurrent.duration._

class HttpSinkBuilderTest extends TestBase {

  behavior of "HttpSinkBuilder"

  val builder = new HttpSinkBuilder

  it should "build correct HttpSink with the default config" in {

    val sink = storyBuilder.buildTaskWithBuilder("""{
        |  "default-request" : {
        |    "uri": "http://www.google.com"
        |  },
        |  "min-backoff": "3 s",
        |  "max-backoff": "1 m",
        |  "pool": {
        |    "max-retries": 10,
        |    "max-open-requests": 64,
        |    "idle-timeout": "10 min"
        |  }
        |}""".stripMargin)(builder)

    val settings = sink.settings

    settings.concurrentRequests shouldEqual 1
    settings.allowExtraSignals shouldEqual true
    settings.expectedResponse shouldEqual Some("ok")
    settings.retryOnError shouldEqual true

    settings.retrySettings.minBackoff shouldEqual 3.seconds
    settings.retrySettings.maxBackoff shouldEqual 1.minute
    settings.retrySettings.randomFactor shouldEqual 0.2
    settings.retrySettings.retryDuration shouldEqual 12.hours

    settings.defaultRequest shouldEqual HttpRequest(
      method = HttpMethods.POST,
      uri = Uri("http://www.google.com")
    )

    settings.connectionPoolSettings.get.maxRetries shouldEqual 10
    settings.connectionPoolSettings.get.maxOpenRequests shouldEqual 64
    settings.connectionPoolSettings.get.idleTimeout shouldEqual 10.minutes
    settings.connectionPoolSettings.get.pipeliningLimit shouldEqual 1
    settings.connectionPoolSettings.get.maxConnections shouldEqual 4
    settings.connectionPoolSettings.get.minConnections shouldEqual 0
  }

  it should "build correct sink with the full config" in {

    val sink = storyBuilder.buildTaskWithBuilder(
      """{
        |  "default-request" : {
        |    "method": "PUT",
        |    "protocol": "http/1.0",
        |    "uri": "http://www.bing.com",
        |    "headers": {
        |      "X-Forwarded-For": "127.0.0.1",
        |      "user-agent": "custom-agent",
        |      "referer": "http://www.google.com"
        |    }
        |  },
        |  "retry-on-error": false,
        |  "concurrent-requests": 100,
        |  "expected-response": "",
        |  "allow-extra-signals": false,
        |  "min-backoff": "3 s",
        |  "max-backoff": "1 m",
        |  "random-factor": 0.8,
        |  "retry-duration": "10 m",
        |  "pool": {
        |    "max-retries": 11,
        |    "max-open-requests": 64,
        |    "idle-timeout": "1 min"
        |  }
        |}""".stripMargin
    )(builder)

    val settings = sink.settings

    settings.concurrentRequests shouldEqual 100
    settings.allowExtraSignals shouldEqual false
    settings.expectedResponse shouldEqual None
    settings.retryOnError shouldEqual false

    settings.retrySettings.minBackoff shouldEqual 3.seconds
    settings.retrySettings.maxBackoff shouldEqual 1.minute
    settings.retrySettings.randomFactor shouldEqual 0.8
    settings.retrySettings.retryDuration shouldEqual 10.minutes

    settings.defaultRequest.method shouldEqual HttpMethods.PUT
    settings.defaultRequest.uri shouldEqual Uri("http://www.bing.com")
    settings.defaultRequest.protocol shouldEqual HttpProtocols.`HTTP/1.0`
    settings.defaultRequest.headers.size shouldEqual 3

    settings.connectionPoolSettings.get.maxRetries shouldEqual 11
    settings.connectionPoolSettings.get.maxOpenRequests shouldEqual 64
    settings.connectionPoolSettings.get.idleTimeout shouldEqual 1.minutes
    settings.connectionPoolSettings.get.pipeliningLimit shouldEqual 1
    settings.connectionPoolSettings.get.maxConnections shouldEqual 4
    settings.connectionPoolSettings.get.minConnections shouldEqual 0
  }

}
