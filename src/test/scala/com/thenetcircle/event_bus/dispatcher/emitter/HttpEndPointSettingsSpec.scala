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

import akka.http.scaladsl.model.{HttpMethods, Uri}
import com.thenetcircle.event_bus.testkit.AkkaStreamSpec
import com.typesafe.config.ConfigFactory

class HttpEmitterSettingsSpec extends AkkaStreamSpec {

  behavior of "HttpEmitterSettings"

  it should "properly be parsed from typesafe Config" in {
    val config = ConfigFactory.parseString("""
        |{
        |  name = TestEmitter
        |  max-retry-times = 10
        |  akka.http.host-connection-pool {
        |    max-connections = 100
        |    min-connections = 10
        |    max-open-requests = 16
        |  }
        |  request {
        |    host = 127.0.0.1
        |    port = 8888
        |    method = GET
        |    uri = /abc
        |  }
        |  expected-response-data = "OK"
        |}
      """.stripMargin)

    val settings = HttpEmitterSettings(config)

    settings.name shouldEqual "TestEmitter"

    settings.host shouldEqual "127.0.0.1"
    settings.port shouldEqual 8888
    settings.defaultRequest.method shouldEqual HttpMethods.GET
    settings.defaultRequest.uri shouldEqual Uri("/abc")

    settings.expectedResponse shouldBe defined
    settings.expectedResponse.get shouldEqual "OK"

    settings.connectionPoolSettings.maxConnections shouldEqual 100
    settings.connectionPoolSettings.minConnections shouldEqual 10
    settings.connectionPoolSettings.maxOpenRequests shouldEqual 16
    settings.connectionPoolSettings.pipeliningLimit shouldEqual 1
  }

  it should "use default values if the fields not set" in {
    val config = ConfigFactory.parseString("""
                                             |{
                                             |  name = TestDefaultEmitter
                                             |  request.host = 127.0.0.2
                                             |}
                                           """.stripMargin)

    val settings = HttpEmitterSettings(config)

    settings.name shouldEqual "TestDefaultEmitter"

    settings.host shouldEqual "127.0.0.2"
    settings.port shouldEqual 80
    settings.defaultRequest.method shouldEqual HttpMethods.POST
    settings.defaultRequest.uri shouldEqual Uri("/")

    settings.expectedResponse shouldBe empty

    settings.connectionPoolSettings.maxConnections shouldEqual 4
    settings.connectionPoolSettings.minConnections shouldEqual 0
    settings.connectionPoolSettings.maxOpenRequests shouldEqual 32
    settings.connectionPoolSettings.pipeliningLimit shouldEqual 1
  }

}
