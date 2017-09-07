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

package com.thenetcircle.event_bus.transporter
import com.thenetcircle.event_bus.testkit.AkkaTestSpec
import com.typesafe.config.ConfigFactory

class TransporterSettingsSpec extends AkkaTestSpec {
  behavior of "TransporterSettings"

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

    val settings = TransporterSettings(config)
  }

}
