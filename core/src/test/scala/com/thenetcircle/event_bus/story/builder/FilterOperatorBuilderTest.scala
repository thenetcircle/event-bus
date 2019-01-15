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
import com.thenetcircle.event_bus.story.tasks.operators.{FilterOperatorBuilder, FilterOperatorSettings}

class FilterOperatorBuilderTest extends TestBase {

  behavior of "FilterOperatorBuilder"

  val builder = new FilterOperatorBuilder

  it should "build proper FilterOperator with empty config" in {
    val task = storyBuilder.buildTaskWithBuilder("""{}""".stripMargin)(builder)

    val settings: FilterOperatorSettings = task.settings

    settings.eventNameWhiteList shouldEqual Seq.empty[String]
    settings.eventNameBlackList shouldEqual Seq.empty[String]
    settings.channelWhiteList shouldEqual Seq.empty[String]
    settings.channelBlackList shouldEqual Seq.empty[String]
    settings.allowedTransportModes shouldEqual Seq("ASYNC", "BOTH", "NONE")
    settings.onlyExtras shouldEqual Map.empty[String, String]
  }

  it should "build proper FilterOperator with proper config" in {
    val task = storyBuilder.buildTaskWithBuilder("""{
        |  "event-name-white-list": ["user\\..*", "wio\\..*"],
        |  "event-name-black-list": ["image\\..*"],
        |  "channel-white-list": ["membership", "forum"],
        |  "channel-black-list": ["quick\\-.*"],
        |  "allowed-transport-modes": ["SYNC-PLUS", "SYNC", "BOTH"],
        |  "only-extras": {
        |    "actorId": "1234",
        |    "generatorId": "tnc-event-dispatcher"
        |  }
        |}""".stripMargin)(builder)

    val settings: FilterOperatorSettings = task.settings

    settings.eventNameWhiteList shouldEqual Seq("user\\..*", "wio\\..*")
    settings.eventNameBlackList shouldEqual Seq("image\\..*")
    settings.channelWhiteList shouldEqual Seq("membership", "forum")
    settings.channelBlackList shouldEqual Seq("quick\\-.*")
    settings.allowedTransportModes shouldEqual Seq("SYNC-PLUS", "SYNC", "BOTH")
    settings.onlyExtras shouldEqual Map("actorId" -> "1234", "generatorId" -> "tnc-event-dispatcher")
  }

}
