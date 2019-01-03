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

package com.thenetcircle.event_bus.builder

import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.story.tasks.tnc.{TNCEventFilterBuilder, TNCEventFilterSettings}

class TNCEventFilterBuilderTest extends TestBase {

  behavior of "TNCEventFilterBuilder"

  val builder = new TNCEventFilterBuilder

  it should "build proper TNCEventFilter with empty config" in {
    val sink = builder.build("""{}""".stripMargin)

    val settings: TNCEventFilterSettings = sink.settings

    settings.eventNameWhiteList shouldEqual Seq.empty[String]
    settings.eventNameBlackList shouldEqual Seq.empty[String]
    settings.channelWhiteList shouldEqual Seq.empty[String]
    settings.channelBlackList shouldEqual Seq.empty[String]
    settings.allowedTransportModes shouldEqual Seq("ASYNC", "BOTH", "NONE")
    settings.onlyExtras shouldEqual Map.empty[String, String]
  }

  it should "build proper TNCEventFilter with proper config" in {
    val sink = builder.build("""{
        |"event-name-white-list": ["user\\..*", "wio\\..*"],
        |"event-name-black-list": ["image\\..*"],
        |"channel-white-list": ["membership", "forum"],
        |"channel-black-list": ["quick\\-.*"],
        |"allowed-transport-modes": ["SYNC-PLUS", "SYNC", "BOTH"],
        |"only-extras": {
        |   "actorId": "1234",
        |   "generatorId": "tnc-event-dispatcher"
        |}
        |}""".stripMargin)

    val settings: TNCEventFilterSettings = sink.settings

    settings.eventNameWhiteList shouldEqual Seq("user\\..*", "wio\\..*")
    settings.eventNameBlackList shouldEqual Seq("image\\..*")
    settings.channelWhiteList shouldEqual Seq("membership", "forum")
    settings.channelBlackList shouldEqual Seq("quick\\-.*")
    settings.allowedTransportModes shouldEqual Seq("SYNC-PLUS", "SYNC", "BOTH")
    settings.onlyExtras shouldEqual Map("actorId" -> "1234", "generatorId" -> "tnc-event-dispatcher")
  }

}
