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

package com.thenetcircle.event_bus.pipeline
import akka.kafka.{ConsumerSettings, ProducerSettings}
import com.thenetcircle.event_bus.pipeline.PipelineType.PipelineType
import com.thenetcircle.event_bus.pipeline.kafka.KafkaPipeline.{Key, Value}
import com.thenetcircle.event_bus.pipeline.kafka.{
  KafkaPipelineFactory,
  KafkaPipelineSettings
}
import com.thenetcircle.event_bus.testkit.AkkaBaseSpec
import org.apache.kafka.common.serialization.{
  ByteArrayDeserializer,
  ByteArraySerializer
}

class PipelinePoolSpec extends AkkaBaseSpec {

  behavior of "PipelinePool"

  val testPipelineSettings1 = new KafkaPipelineSettings(
    "TP1",
    ProducerSettings[Key, Value](system,
                                 new ByteArraySerializer,
                                 new ByteArraySerializer),
    ConsumerSettings[Key, Value](system,
                                 new ByteArrayDeserializer,
                                 new ByteArrayDeserializer)
  )
  val testPipelineSettings2 = new KafkaPipelineSettings(
    "TP2",
    ProducerSettings[Key, Value](system,
                                 new ByteArraySerializer,
                                 new ByteArraySerializer),
    ConsumerSettings[Key, Value](system,
                                 new ByteArrayDeserializer,
                                 new ByteArrayDeserializer)
  )
  val testPipelinePool = new PipelinePool(
    Map[String, (PipelineType, PipelineSettings)](
      "TP1" -> (PipelineType.Kafka, testPipelineSettings1),
      "TP2" -> (PipelineType.Kafka, testPipelineSettings2)
    ))

  it should "support to get predefined pipelines" in {

    List("TP1", "TP2").foreach(pipelineName => {
      val _settings =
        if (pipelineName == "TP1") testPipelineSettings1
        else testPipelineSettings2

      testPipelinePool.getPipelineType(pipelineName) shouldBe Some(
        PipelineType.Kafka)

      testPipelinePool.getPipelineSettings(pipelineName) shouldBe Some(
        _settings)
      testPipelinePool.getPipelineFactory(pipelineName) shouldBe Some(
        KafkaPipelineFactory())

      testPipelinePool.getPipeline(pipelineName) shouldBe defined
      testPipelinePool
        .getPipeline(pipelineName)
        .get
        .pipelineSettings shouldBe _settings
      testPipelinePool
        .getPipeline(pipelineName)
        .get
        .pipelineType shouldBe PipelineType.Kafka
    })

  }

  it should "return none for undefined pipelines" in {

    testPipelinePool.getPipelineType("TP3") shouldBe empty
    testPipelinePool.getPipelineSettings("TP3") shouldBe empty
    testPipelinePool.getPipelineFactory("TP3") shouldBe empty
    testPipelinePool.getPipeline("TP3") shouldBe empty

  }
}
