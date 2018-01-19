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
import com.thenetcircle.event_bus.BaseTest

class StoryBuilderTest extends BaseTest {

  behavior of "StoryBuilder"

  /*val builder = new StoryBuilder

  it should "build correct Story based on config" in {

    val story = builder.build(
      """{
        |  "name": "testStory",
        |  "sourceTask": [
        |    "http",
        |    "{\"interface\": \"127.0.0.1\",\"akka\": {  \"http\": {    \"server\": {}  }},\"succeeded-response\": \"okoo\",\"error-response\": \"kooo\",\"max-connections\": 1001}"
        |  ],
        |  "transformTasks": [
        |    ["topic_resolver", "{}"]
        |  ],
        |  "sinkTask": [
        |    "kafka",
        |    "{\"producer\": {  \"close-timeout\": \"100s\",  \"use-dispatcher\": \"test-dispatcher\"}}"
        |  ],
        |  "fallbackTask": [
        |    ["http", "{\"request\" : {\"host\": \"127.0.0.1\"}, \"expected-response\": \"TEST_RESPONSE\"}"]
        |  ]
        |}""".stripMargin
    )

    story.settings.name shouldEqual "testStory"

    story.sourceTask.isInstanceOf[HttpSource] shouldEqual true
    story.sourceTask.asInstanceOf[HttpSource].settings.interface shouldEqual "127.0.0.1"

    story.transformTasks.get.head.isInstanceOf[TopicResolverTransform] shouldEqual true

    story.sinkTask.get.isInstanceOf[KafkaSink] shouldEqual true

    story.fallbackTask.get.head.isInstanceOf[HttpSink] shouldEqual true

  }*/

}
