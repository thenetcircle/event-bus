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
import com.thenetcircle.event_bus.testkit.AkkaBaseSpec

class PipelineConfigFactorySpec extends AkkaBaseSpec {

  behavior of "PipelineConfigFactory"

  it should "properly be initialized from system config" in {
    val testPipelineName = "TestPipeline"

    val t = PipelineConfigPool().getPipelineType(testPipelineName)
    t shouldBe defined
    t.get shouldEqual PipelineType.Kafka

    val c = PipelineConfigPool().getPipelineConfig(testPipelineName)
    c shouldBe defined
    c.get.getString("test-field") shouldEqual "test-field"
  }
}
