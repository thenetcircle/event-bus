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

package com.thenetcircle.event_bus.dispatcher

import com.thenetcircle.event_bus.dispatcher.emitter.HttpEmitterSettings
import com.thenetcircle.event_bus.pipeline.kafka.KafkaPipelineOutletSettings
import com.thenetcircle.event_bus.pipeline.model.PipelineType
import com.thenetcircle.event_bus.testkit.AkkaStreamSpec
import com.typesafe.config.ConfigFactory

class DispatcherSettingsSpec extends AkkaStreamSpec {

  behavior of "DispatcherSettings"

  it should "properly be parsed from typesafe Config" in {
    val config = ConfigFactory.parseString("""
                                             |{
                                             |  name = TestDispatcher
                                             |  emitters = [{
                                             |    type = http
                                             |    name = TestEmitter
                                             |    request.host = 127.0.0.1
                                             |  }]
                                             |  pipeline {
                                             |    name = TestPipeline
                                             |    outlet {
                                             |      group-id = TestDispatcher
                                             |      topics = ["default"]
                                             |      extract-parallelism = 10
                                             |      commit-parallelism = 10
                                             |      commit-batch-max = 10
                                             |    }
                                             |  }
                                             |  akka.stream.materializer {
                                             |    initial-input-buffer-size = 16
                                             |    max-input-buffer-size = 64
                                             |    dispatcher = TestDispatcher
                                             |    debug-logging = on
                                             |  }
                                             |}
                                           """.stripMargin)

    val settings = DispatcherSettings(config)

    settings.name shouldEqual "TestDispatcher"

    settings.emitterSettings.size shouldEqual 1
    settings.emitterSettings(0).name shouldEqual "TestEmitter"
    settings.emitterSettings(0) shouldBe a[HttpEmitterSettings]

    settings.pipelineOutlet.pipeline._type shouldEqual PipelineType.Kafka
    settings.pipelineOutlet.pipeline.settings.name shouldEqual "TestPipeline"
    settings.pipelineOutlet.settings shouldBe a[KafkaPipelineOutletSettings]

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
                                             |  name = TestDefaultDispatcher
                                             |  emitters = [{
                                             |    type = http
                                             |    name = TestEmitter
                                             |    request.host = 127.0.0.1
                                             |  }]
                                             |  pipeline {
                                             |    name = TestPipeline
                                             |    outlet {
                                             |      group-id = TestDispatcher
                                             |      topics = ["default"]
                                             |      extract-parallelism = 10
                                             |      commit-parallelism = 10
                                             |      commit-batch-max = 10
                                             |    }
                                             |  }
                                             |}
                                           """.stripMargin)

    val settings = DispatcherSettings(config)

    settings.name shouldEqual "TestDefaultDispatcher"
    settings.materializerSettings shouldBe empty
  }

}
