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

package com.thenetcircle.event_bus.story
import com.thenetcircle.event_bus.base.AkkaStreamTest
import com.thenetcircle.event_bus.plots.http.{HttpSink, HttpSource}
import com.thenetcircle.event_bus.plots.kafka.KafkaSink
import com.thenetcircle.event_bus.plots.resolvers.TopicResolver

class StoryBuilderTest extends AkkaStreamTest {

  behavior of "StoryBuilder"

  val builder = new StoryBuilder

  it should "build correct Story based on config" in {

    val story = builder.build(
      """{
        |  "name": "testStory",
        |  "source": [
        |    "http",
        |    "{\"interface\": \"127.0.0.1\",\"akka\": {  \"http\": {    \"server\": {}  }},\"succeeded-response\": \"okoo\",\"error-response\": \"kooo\",\"max-connections\": 1001}"
        |  ],
        |  "ops": [
        |    ["topic_resolver", "{}"]
        |  ],
        |  "sink": [
        |    "kafka",
        |    "{\"producer\": {  \"close-timeout\": \"100s\",  \"use-dispatcher\": \"test-dispatcher\"}}"
        |  ],
        |  "fallback": [
        |    "http",
        |    "{\"request\" : {\"host\": \"127.0.0.1\"}, \"expected-response\": \"TEST_RESPONSE\"}"
        |  ]
        |}""".stripMargin
    )

    story.settings.name shouldEqual "testStory"

    story.source.isInstanceOf[HttpSource]
    story.source.asInstanceOf[HttpSource].settings.interface shouldEqual "127.0.0.1"

    story.ops.get.head.isInstanceOf[TopicResolver]

    story.sink.get.isInstanceOf[KafkaSink]

    story.fallback.get.isInstanceOf[HttpSink]

  }

}
