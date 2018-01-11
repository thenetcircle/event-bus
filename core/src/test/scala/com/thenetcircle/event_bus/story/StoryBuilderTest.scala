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
import com.thenetcircle.event_bus.tasks.http.{HttpTaskC, HttpTaskA}
import com.thenetcircle.event_bus.tasks.kafka.KafkaTaskC
import com.thenetcircle.event_bus.tasks.misc.TopicResolverTaskB

class StoryBuilderTest extends AkkaStreamTest {

  behavior of "StoryBuilder"

  val builder = new StoryBuilder

  it should "build correct Story based on config" in {

    val story = builder.build(
      """{
        |  "name": "testStory",
        |  "taskA": [
        |    "http",
        |    "{\"interface\": \"127.0.0.1\",\"akka\": {  \"http\": {    \"server\": {}  }},\"succeeded-response\": \"okoo\",\"error-response\": \"kooo\",\"max-connections\": 1001}"
        |  ],
        |  "taskB": [
        |    ["topic_resolver", "{}"]
        |  ],
        |  "taskC": [
        |    "kafka",
        |    "{\"producer\": {  \"close-timeout\": \"100s\",  \"use-dispatcher\": \"test-dispatcher\"}}"
        |  ],
        |  "alternativeC": [
        |    ["http", "{\"request\" : {\"host\": \"127.0.0.1\"}, \"expected-response\": \"TEST_RESPONSE\"}"]
        |  ]
        |}""".stripMargin
    )

    story.settings.name shouldEqual "testStory"

    story.taskA.isInstanceOf[HttpTaskA] shouldEqual true
    story.taskA.asInstanceOf[HttpTaskA].settings.interface shouldEqual "127.0.0.1"

    story.taskB.get.head.isInstanceOf[TopicResolverTaskB] shouldEqual true

    story.taskC.get.isInstanceOf[KafkaTaskC] shouldEqual true

    story.alternativeC.get.head.isInstanceOf[HttpTaskC] shouldEqual true

  }

}
