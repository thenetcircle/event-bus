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
import com.thenetcircle.event_bus.transporter.entrypoint.HttpEntryPointSettings
import com.typesafe.config.ConfigFactory

class TransporterSettingsSpec extends AkkaTestSpec {
  behavior of "TransporterSettings"

  it should "properly be parsed from typesafe Config" in {
    val config = ConfigFactory.parseString("""
                                             |{
                                             |  name = TestTransporter
                                             |  entrypoints = [
                                             |    {
                                             |      type = http
                                             |      name = TestEntryPoint1
                                             |      interface = 127.0.0.1
                                             |      port = 8080
                                             |    }
                                             |    {
                                             |      type = http
                                             |      name = TestEntryPoint2
                                             |      interface = 127.0.0.2
                                             |      port = 8081
                                             |    }
                                             |  ]
                                             |  pipeline {
                                             |    name = TestPipeline
                                             |    inlet-settings {}
                                             |  }
                                             |  transport-parallelism = 11
                                             |  commit-parallelism = 11
                                             |  akka.stream.materializer {}
                                             |}
                                           """.stripMargin)

    val settings = TransporterSettings(config)

    settings.name shouldEqual "TestTransporter"
    settings.transportParallelism shouldEqual 11
    settings.commitParallelism shouldEqual 11

    settings.entryPointsSettings(0) shouldBe a[HttpEntryPointSettings]
    settings.entryPointsSettings(1) shouldBe a[HttpEntryPointSettings]

    val entryPoint0 =
      settings.entryPointsSettings(0).asInstanceOf[HttpEntryPointSettings]
    entryPoint0.name shouldEqual "TestEntryPoint1"
    entryPoint0.interface shouldEqual "127.0.0.1"
    entryPoint0.port shouldEqual 8080

    val entryPoint1 =
      settings.entryPointsSettings(1).asInstanceOf[HttpEntryPointSettings]
    entryPoint1.name shouldEqual "TestEntryPoint2"
    entryPoint1.interface shouldEqual "127.0.0.2"
    entryPoint1.port shouldEqual 8081
  }

}
