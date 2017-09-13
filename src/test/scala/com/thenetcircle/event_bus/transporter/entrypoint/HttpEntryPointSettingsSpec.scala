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

package com.thenetcircle.event_bus.transporter.entrypoint

import com.thenetcircle.event_bus.EventFormat
import com.thenetcircle.event_bus.testkit.AkkaStreamSpec
import com.typesafe.config.ConfigFactory

class HttpEntryPointSettingsSpec extends AkkaStreamSpec {

  behavior of "HttpEntryPointSettings"

  it should "properly be parsed from typesafe Config" in {
    val config = ConfigFactory.parseString("""
        |{
        |  name = TestEntryPoint
        |  priority = high
        |  max-connections = 10
        |  pre-connection-parallelism = 5
        |  event-format = test
        |  akka.http.server {
        |    pipelining-limit = 999
        |    response-header-size-hint = 999
        |    backlog = 999
        |  }
        |  interface = 127.0.0.1
        |  port = 8888
        |}
      """.stripMargin)

    val settings = HttpEntryPointSettings(config)

    settings.name shouldEqual "TestEntryPoint"
    settings.priority shouldEqual EntryPointPriority.High
    settings.maxConnections shouldEqual 10
    settings.perConnectionParallelism shouldEqual 5
    settings.eventFormat shouldEqual EventFormat.TestFormat
    settings.interface shouldEqual "127.0.0.1"
    settings.port shouldEqual 8888
    settings.serverSettings.pipeliningLimit shouldEqual 999
    settings.serverSettings.responseHeaderSizeHint shouldEqual 999
    settings.serverSettings.backlog shouldEqual 999
  }

  it should "use default values if the fields not set" in {
    val config = ConfigFactory.parseString("""
                                             |{
                                             |  name = TestEntryPoint
                                             |  interface = localhost
                                             |  port = 8080
                                             |}
                                           """.stripMargin)

    val settings = HttpEntryPointSettings(config)

    settings.name shouldEqual "TestEntryPoint"
    settings.priority shouldEqual EntryPointPriority.Normal
    settings.maxConnections shouldEqual 1000
    settings.perConnectionParallelism shouldEqual 10
    settings.eventFormat shouldEqual EventFormat.DefaultFormat
    settings.interface shouldEqual "localhost"
    settings.port shouldEqual 8080
    settings.serverSettings.pipeliningLimit shouldEqual 16
    settings.serverSettings.responseHeaderSizeHint shouldEqual 512
    settings.serverSettings.backlog shouldEqual 100
  }

}
