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

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import com.thenetcircle.event_bus.base.AkkaStreamTest

import scala.concurrent.duration._

class HttpSinkBuilderTest extends AkkaStreamTest {

  behavior of "HttpSinkBuilder"

  val builder = new HttpSinkBuilder

  it should "build correct HttpSink with the default config" in {

    val sink = builder.build("""{
        |  "request" : {
        |    "uri": "http://www.google.com"
        |  },
        |  "expected-response": "TEST_RESPONSE"
        |}""".stripMargin)

    val settings = sink.settings

    settings.maxRetryTimes shouldEqual 10
    settings.maxConcurrentRetries shouldEqual 1
    settings.retryTimeout shouldEqual 10.seconds
    settings.defaultRequest shouldEqual HttpRequest(
      method = HttpMethods.POST,
      uri = Uri("http://www.google.com")
    )
    settings.expectedResponseBody shouldEqual "TEST_RESPONSE"
    settings.poolSettings shouldBe defined

  }

}
