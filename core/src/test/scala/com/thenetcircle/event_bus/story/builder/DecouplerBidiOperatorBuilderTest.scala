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

package com.thenetcircle.event_bus.story.builder

import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.story.interfaces.{IOperator, ISink}
import com.thenetcircle.event_bus.story.tasks.kafka.{KafkaSink, KafkaSinkBuilder, KafkaSinkSettings}
import com.thenetcircle.event_bus.story.tasks.operators._

import scala.concurrent.duration._

class DecouplerBidiOperatorBuilderTest extends TestBase {

  behavior of "DecouplerBidiOperatorBuilder"

  val builder = new DecouplerBidiOperatorBuilder

  it should "build proper operator with default config" in {
    val operator = storyBuilder.buildTaskWithBuilder("""{}""".stripMargin)(builder)

    val settings: DecouplerSettings = operator.settings

    settings.bufferSize shouldEqual 10000
    settings.terminateDelay shouldEqual (10 minutes)
    settings.secondarySink shouldEqual None
    settings.secondarySinkBufferSize shouldEqual 1000
  }

  it should "build proper operator with another operator as failover" in {
    storyBuilder.addTaskBuilder[IOperator](classOf[FileOperatorBuilder].getName)
    builder.setStoryBuilder(storyBuilder)
    val operator = storyBuilder.buildTaskWithBuilder(
      """{
        |  "buffer-size": 50,
        |  "terminate-delay": "20 m",
        |  "secondary-sink": "file#{\"path\":\"/tmp/secondary-sink.log\", \"event-delimiter\": \"#-#-#\"}",
        |  "secondary-sink-buffer-size": 10
        |}""".stripMargin
    )(builder)

    val settings: DecouplerSettings = operator.settings

    settings.bufferSize shouldEqual 50
    settings.terminateDelay shouldEqual (20 minutes)
    settings.secondarySink.get shouldBe a[FileOperator]
    settings.secondarySink.get.asInstanceOf[FileOperator].settings shouldEqual FileOperatorSettings(
      path = "/tmp/secondary-sink.log",
      lineDelimiter = "<tab>",
      eventDelimiter = "#-#-#"
    )
    settings.secondarySinkBufferSize shouldEqual 10
  }

  it should "build proper operator with another sink as failover" in {
    storyBuilder.addTaskBuilder[ISink](classOf[KafkaSinkBuilder].getName)
    builder.setStoryBuilder(storyBuilder)
    val operator = storyBuilder.buildTaskWithBuilder(
      """{
        |  "buffer-size": 50,
        |  "terminate-delay": "20 m",
        |  "secondary-sink": "kafka#{\"bootstrap-servers\": \"localhost:9092\"}",
        |  "secondary-sink-buffer-size": 10
        |}""".stripMargin
    )(builder)

    val settings: DecouplerSettings = operator.settings

    settings.bufferSize shouldEqual 50
    settings.terminateDelay shouldEqual (20 minutes)
    settings.secondarySink.get shouldBe a[KafkaSink]
    settings.secondarySink.get.asInstanceOf[KafkaSink].settings.bootstrapServers shouldEqual "localhost:9092"
    settings.secondarySinkBufferSize shouldEqual 10
  }

}
