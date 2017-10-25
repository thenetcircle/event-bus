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

import com.thenetcircle.event_bus.pipeline.PipelineType
import com.thenetcircle.event_bus.pipeline.kafka.KafkaPipelineSettings
import com.thenetcircle.event_bus.testkit.AkkaStreamSpec
import com.thenetcircle.event_bus.transporter.receiver.HttpReceiverSettings
import com.typesafe.config.ConfigFactory

class TransporterSettingsSpec extends AkkaStreamSpec {
  behavior of "TransporterSettings"

  it should "properly be parsed from typesafe Config" in {
    val config = ConfigFactory.parseString("""
                                             |{
                                             |  name = TestTransporter
                                             |  transport-parallelism = 11
                                             |  commit-parallelism = 11
                                             |  receivers = [
                                             |    {
                                             |      type = http
                                             |      name = TestReceiver1
                                             |      interface = 127.0.0.1
                                             |      port = 8080
                                             |    }
                                             |    {
                                             |      type = http
                                             |      name = TestReceiver2
                                             |      interface = 127.0.0.2
                                             |      port = 8081
                                             |    }
                                             |  ]
                                             |  pipeline {
                                             |    name = TestPipeline
                                             |    inlet-settings {}
                                             |  }
                                             |  akka.stream.materializer {
                                             |    initial-input-buffer-size = 16
                                             |    max-input-buffer-size = 64
                                             |    dispatcher = TestDispatcher
                                             |    debug-logging = on
                                             |  }
                                             |}
                                           """.stripMargin)

    val settings = TransporterSettings(config)

    settings.name shouldEqual "TestTransporter"
    settings.transportParallelism shouldEqual 11
    settings.commitParallelism shouldEqual 11

    settings.receiverSettings(0) shouldBe a[HttpReceiverSettings]
    settings.receiverSettings(1) shouldBe a[HttpReceiverSettings]

    val receiver1 =
      settings.receiverSettings(0).asInstanceOf[HttpReceiverSettings]
    receiver1.name shouldEqual "TestReceiver1"
    receiver1.interface shouldEqual "127.0.0.1"
    receiver1.port shouldEqual 8080

    val receiver2 =
      settings.receiverSettings(1).asInstanceOf[HttpReceiverSettings]
    receiver2.name shouldEqual "TestReceiver2"
    receiver2.interface shouldEqual "127.0.0.2"
    receiver2.port shouldEqual 8081

    settings.pipeline.pipelineType shouldEqual PipelineType.Kafka
    settings.pipeline.pipelineSettings.name shouldEqual "TestPipeline"
    settings.pipeline.pipelineSettings shouldBe a[KafkaPipelineSettings]

    settings.materializerSettings shouldBe defined
    settings.materializerSettings.get.initialInputBufferSize shouldEqual 16
    settings.materializerSettings.get.maxInputBufferSize shouldEqual 64
    settings.materializerSettings.get.dispatcher shouldEqual "TestDispatcher"
    settings.materializerSettings.get.debugLogging shouldEqual true
    settings.materializerSettings.get.maxFixedBufferSize shouldEqual 1000000000 // default value
  }

  it should "use default values if the fields not set" in {
    val config = ConfigFactory.parseString("""
                                             |{
                                             |  name = TestDefaultTransporter
                                             |  receivers = [
                                             |    {
                                             |      type = http
                                             |      name = TestReceiver1
                                             |      interface = 127.0.0.1
                                             |      port = 8080
                                             |    }
                                             |  ]
                                             |  pipeline {
                                             |    name = TestPipeline
                                             |    inlet-settings {}
                                             |  }
                                             |}
                                           """.stripMargin)

    val settings = TransporterSettings(config)

    settings.name shouldEqual "TestDefaultTransporter"
    settings.transportParallelism shouldEqual 1
    settings.commitParallelism shouldEqual 10
    settings.materializerSettings shouldBe empty
  }

}
