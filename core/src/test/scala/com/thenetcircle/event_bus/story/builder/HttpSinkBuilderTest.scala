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
import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.story.tasks.http.HttpSinkBuilder

import scala.concurrent.duration._

class HttpSinkBuilderTest extends TestBase {

  behavior of "HttpSinkBuilder"

  val builder = new HttpSinkBuilder

  it should "build correct HttpSink with minimum config" in {

    val task = storyBuilder.buildTaskWithBuilder("""{
                                                   |  "default-request" : {
                                                   |    "method": "GET",
                                                   |    "uri": "http://www.google.com"
                                                   |  }
                                                   |}""".stripMargin)(builder)

    val settings = task.settings

    settings.concurrentRequests shouldEqual 1
    settings.requestBufferSize shouldEqual 100
    settings.expectedResponse shouldEqual Some("ok")
    settings.allowExtraSignals shouldEqual true
    settings.useHttpsConnectionPool shouldEqual false

    settings.useRetrySender shouldEqual true
    settings.retrySenderSettings.minBackoff shouldEqual 1.second
    settings.retrySenderSettings.maxBackoff shouldEqual 30.seconds
    settings.retrySenderSettings.randomFactor shouldEqual 0.2
    settings.retrySenderSettings.retryDuration shouldEqual 12.hours

    settings.defaultRequest.method shouldEqual HttpMethods.GET
    settings.defaultRequest.uri shouldEqual Uri("http://www.google.com")
    settings.defaultRequest.protocol shouldEqual HttpProtocols.`HTTP/1.1`

    settings.connectionPoolSettings.get.maxConnections shouldEqual 32
    settings.connectionPoolSettings.get.minConnections shouldEqual 0
    settings.connectionPoolSettings.get.maxOpenRequests shouldEqual 256
    settings.connectionPoolSettings.get.maxRetries shouldEqual 5
    settings.connectionPoolSettings.get.idleTimeout shouldEqual 30.seconds
    settings.connectionPoolSettings.get.pipeliningLimit shouldEqual 1
  }

  it should "build correct HttpSink with the default config" in {

    val sink = storyBuilder.buildTaskWithBuilder("""{
        |  "default-request" : {
        |    "uri": "http://www.google.com"
        |  },
        |  "retry-sender": {
        |    "min-backoff": "3 s",
        |    "max-backoff": "1 m"
        |  },
        |  "pool": {
        |    "max-retries": 10,
        |    "max-open-requests": 64,
        |    "idle-timeout": "10 min"
        |  }
        |}""".stripMargin)(builder)

    val settings = sink.settings

    settings.concurrentRequests shouldEqual 1
    settings.requestBufferSize shouldEqual 100
    settings.expectedResponse shouldEqual Some("ok")
    settings.allowExtraSignals shouldEqual true

    settings.useRetrySender shouldEqual true
    settings.retrySenderSettings.minBackoff shouldEqual 3.seconds
    settings.retrySenderSettings.maxBackoff shouldEqual 1.minute
    settings.retrySenderSettings.randomFactor shouldEqual 0.2
    settings.retrySenderSettings.retryDuration shouldEqual 12.hours

    settings.defaultRequest.method shouldEqual HttpMethods.POST
    settings.defaultRequest.uri shouldEqual Uri("http://www.google.com")
    settings.defaultRequest.protocol shouldEqual HttpProtocols.`HTTP/1.1`

    settings.connectionPoolSettings.get.maxConnections shouldEqual 32
    settings.connectionPoolSettings.get.minConnections shouldEqual 0
    settings.connectionPoolSettings.get.maxOpenRequests shouldEqual 64
    settings.connectionPoolSettings.get.maxRetries shouldEqual 10
    settings.connectionPoolSettings.get.idleTimeout shouldEqual 10.minutes
    settings.connectionPoolSettings.get.pipeliningLimit shouldEqual 1
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
        |  "concurrent-requests": 100,
        |  "request-buffer-size": 1000,
        |  "expected-response": "",
        |  "allow-extra-signals": false,
        |  "use-retry-sender": false,
        |  "retry-sender": {
        |    "min-backoff": "3 s",
        |    "max-backoff": "1 m",
        |    "random-factor": 0.8,
        |    "retry-duration": "10 m"
        |  },
        |  "pool": {
        |    "max-retries": 11,
        |    "max-open-requests": 64,
        |    "idle-timeout": "1 min"
        |  }
        |}""".stripMargin
    )(builder)

    val settings = sink.settings

    settings.concurrentRequests shouldEqual 100
    settings.requestBufferSize shouldEqual 1000
    settings.allowExtraSignals shouldEqual false
    settings.expectedResponse shouldEqual None

    settings.useRetrySender shouldEqual false
    settings.retrySenderSettings.minBackoff shouldEqual 3.seconds
    settings.retrySenderSettings.maxBackoff shouldEqual 1.minute
    settings.retrySenderSettings.randomFactor shouldEqual 0.8
    settings.retrySenderSettings.retryDuration shouldEqual 10.minutes

    settings.defaultRequest.method shouldEqual HttpMethods.PUT
    settings.defaultRequest.uri shouldEqual Uri("http://www.bing.com")
    settings.defaultRequest.protocol shouldEqual HttpProtocols.`HTTP/1.0`
    settings.defaultRequest.headers.size shouldEqual 3

    settings.connectionPoolSettings.get.maxRetries shouldEqual 11
    settings.connectionPoolSettings.get.maxOpenRequests shouldEqual 64
    settings.connectionPoolSettings.get.idleTimeout shouldEqual 1.minute
    settings.connectionPoolSettings.get.pipeliningLimit shouldEqual 1
    settings.connectionPoolSettings.get.maxConnections shouldEqual 32
    settings.connectionPoolSettings.get.minConnections shouldEqual 0
  }

}
