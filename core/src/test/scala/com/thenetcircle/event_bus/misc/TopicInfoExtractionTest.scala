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

package com.thenetcircle.event_bus.misc

import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.tasks.tnc.{TopicInfo, TopicInfoProtocol}
import spray.json._

class TopicInfoExtractionTest extends TestBase {

  behavior of "TopicInfoExtraction"

  import TopicInfoProtocol._

  it should "do it correctly" in {

    val data =
      """
        |[
        |  {
        |    "topic": "test-topic-01111",
        |    "patterns": [
        |      "user\\..*",
        |      "message\\..*"
        |    ]
        |  },
        |  {
        |    "topic": "test-topic-02",
        |    "patterns": [
        |      "feature\\..*",
        |      "click\\..*"
        |    ]
        |  },
        |  {
        |    "topic": "test-topic-03",
        |    "channels": [
        |      "chan01\\..*"
        |    ],
        |    "patterns": [
        |      "test\\..*"
        |    ]
        |  },
        |  {
        |    "topic": "test-default-topic",
        |    "patterns": [
        |      ".*"
        |    ]
        |  }
        |]
      """.stripMargin

    val topicInfoList = data.parseJson.convertTo[List[TopicInfo]]

    topicInfoList.length shouldEqual 4
    topicInfoList(1) shouldEqual TopicInfo("test-topic-02", Some(List("feature\\..*", "click\\..*")), None)
    topicInfoList(2) shouldEqual TopicInfo("test-topic-03", Some(List("test\\..*")), Some(List("chan01\\..*")))
    topicInfoList(3) shouldEqual TopicInfo("test-default-topic", Some(List(".*")), None)

  }
}
