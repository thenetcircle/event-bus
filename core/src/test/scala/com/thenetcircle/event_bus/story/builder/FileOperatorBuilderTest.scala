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
import com.thenetcircle.event_bus.story.tasks.operators.{FileOperatorBuilder, FileOperatorSettings}

class FileOperatorBuilderTest extends TestBase {

  behavior of "FileOperatorBuilder"

  val builder = new FileOperatorBuilder

  it should "build proper operator with default config" in {
    val operator = storyBuilder.buildTaskWithBuilder("""{
        |  "path": "/tmp/failover.log"
        |}""".stripMargin)(builder)

    val settings: FileOperatorSettings = operator.settings

    settings.path shouldEqual "/tmp/failover.log"
    settings.contentDelimiter shouldEqual "<tab>"
    settings.eventDelimiter shouldEqual "<newline>#-:#:-#<newline>"
  }

  it should "build proper operator with proper config" in {
    val operator = storyBuilder.buildTaskWithBuilder("""{
        |  "path": "/tmp/test.log",
        |  "content-delimiter": "<newline>",
        |  "event-delimiter": "#-#-#"
        |}""".stripMargin)(builder)

    val settings: FileOperatorSettings = operator.settings

    settings.path shouldEqual "/tmp/test.log"
    settings.contentDelimiter shouldEqual "<newline>"
    settings.eventDelimiter shouldEqual "#-#-#"
  }

}
