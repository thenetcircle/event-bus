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

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
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

    settings.minBackoff shouldEqual 3.seconds
    settings.maxBackoff shouldEqual 1.minute
    settings.randomFactor shouldEqual 0.2
    settings.maxRetryTime shouldEqual 12.hours
    settings.concurrentRequests shouldEqual 1

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

}
