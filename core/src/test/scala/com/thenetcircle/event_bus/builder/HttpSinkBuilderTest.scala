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

package com.thenetcircle.event_bus.builder

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.tasks.http.HttpSinkBuilder

import scala.concurrent.duration._

class HttpSinkBuilderTest extends TestBase {

  behavior of "HttpSinkBuilder"

  val builder = new HttpSinkBuilder

  it should "build correct HttpSink with the default config" in {

    val sink = builder.build("""{
        |  "request" : {
        |    "uri": "http://www.google.com"
        |  },
        |  "min-backoff": "3 s",
        |  "max-backoff": "1 m",
        |  "pool": {
        |    "max-retries": 10,
        |    "max-open-requests": 64,
        |    "idle-timeout": "10 min"
        |  }
        |}""".stripMargin)

    val settings = sink.settings

    settings.minBackoff shouldEqual 3.seconds
    settings.maxBackoff shouldEqual 1.minute
    settings.randomFactor shouldEqual 0.2
    settings.maxRetryTime shouldEqual 12.hours
    settings.concurrentRetries shouldEqual 1

    settings.defaultRequest shouldEqual HttpRequest(
      method = HttpMethods.POST,
      uri = Uri("http://www.google.com")
    )
    settings.poolSettings.get.maxRetries shouldEqual 10
    settings.poolSettings.get.maxOpenRequests shouldEqual 64
    settings.poolSettings.get.idleTimeout shouldEqual 10.minutes
    settings.poolSettings.get.pipeliningLimit shouldEqual 1
    settings.poolSettings.get.maxConnections shouldEqual 4
    settings.poolSettings.get.minConnections shouldEqual 0

  }

}
