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
import com.thenetcircle.event_bus.event.extractor.DataFormat
import com.thenetcircle.event_bus.story.tasks.http.HttpSourceBuilder

import scala.concurrent.duration._

class HttpSourceBuilderTest extends TestBase {

  behavior of "HttpSourceBuilder"

  val builder = new HttpSourceBuilder

  it should "build correct HttpSource with the default config" in {

    val source = builder.build("""{
        |  "interface": "127.0.0.1",
        |  "port": 8888,
        |  "succeeded-response": "okoo",
        |  "server": {
        |    "max-connections": 1001,
        |    "request-timeout": "5 s"
        |  }
        |}""".stripMargin)

    val settings = source.settings

    settings.interface shouldEqual "127.0.0.1"
    settings.port shouldEqual 8888
    settings.format shouldEqual DataFormat.ACTIVITYSTREAMS
    settings.succeededResponse shouldEqual "okoo"
    settings.serverSettings.get.maxConnections shouldEqual 1001
    settings.serverSettings.get.requestTimeout shouldEqual 5.seconds
    settings.serverSettings.get.lingerTimeout shouldEqual 1.minute

  }
}
