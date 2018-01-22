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

package com.thenetcircle.event_bus.tasks.misc
import com.thenetcircle.event_bus.BaseTest
import com.thenetcircle.event_bus.helper.{ConfigStringParser, ZKManager}

class TNCKafkaTopicResolverTest extends BaseTest {

  behavior of "TNCKafkaTopicResolver"

  val zkManager =
    ZKManager.init("maggie-zoo-1:2181,maggie-zoo-2:2181", s"/event-bus/${appContext.getAppName()}")
  val resolver = new TNCKafkaTopicResolver(zkManager, "event-default")
  val delimiter = ConfigStringParser.delimiter
  resolver.updateMapping(
    Map(
      "event-user" -> Map("patterns" -> s"""user.*${delimiter}profile.*"""),
      "event-message" -> Map("patterns" -> """message.*""")
    )
  )

  it should "solve topic correctly" in {

    val testEvent1 = createTestEvent("message.send")
    resolver.resolveEvent(testEvent1).metadata.group shouldEqual Some("event-message")

    val testEvent2 = createTestEvent("profile.kick")
    resolver.resolveEvent(testEvent2).metadata.group shouldEqual Some("event-user")

    val testEvent3 = createTestEvent("user.visit.profile")
    resolver.resolveEvent(testEvent3).metadata.group shouldEqual Some("event-user")

    val testEvent4 = createTestEvent("payment.buy")
    resolver.resolveEvent(testEvent4).metadata.group shouldEqual Some("event-default")

    /*val executionStart: Long = currentTime

    val done = Source(0 to 100000)
      .map(i => createTestEvent(s"message.kick.$i"))
      .via(resolver.prepare())
      .runForeach {
        case (resultTry, event) =>
          println(event.metadata.group)
      }

    done.foreach(_ => {
      val total = currentTime - executionStart
      Console.println("[total " + total + "ms]")
    })*/

  }

}
