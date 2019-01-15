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

package com.thenetcircle.event_bus.story.tasks.operators

import com.thenetcircle.event_bus.IntegrationTestBase

class TopicResolverOperatorTest extends IntegrationTestBase {

  behavior of "TopicResolverOperator"

  val resolver = new TopicResolverOperator(zkManager, "event-default")
  resolver.init()

  it should "solve topic correctly" in {

    // ------ test resolving by event name
    resolver
      .resolveEvent(
        createTestEvent("unknown")
      )
      .metadata
      .topic shouldEqual Some("event-default")

    resolver
      .resolveEvent(
        createTestEvent("membership.test")
      )
      .metadata
      .topic shouldEqual Some(
      "event-membership"
    )

    resolver
      .resolveEvent(
        createTestEvent("user.test")
      )
      .metadata
      .topic shouldEqual Some(
      "event-user"
    )

    // ------ test resolving by event channel
    resolver
      .resolveEvent(
        createTestEvent("user.test", channel = Some("mychannel"))
      )
      .metadata
      .topic shouldEqual Some("event-user")

    resolver
      .resolveEvent(
        createTestEvent("user.test", channel = Some("mem.normal"))
      )
      .metadata
      .topic shouldEqual Some("event-membership")

    resolver
      .resolveEvent(
        createTestEvent("user.test", channel = Some("dino"))
      )
      .metadata
      .topic shouldEqual Some("event-wio")

    /*val executionStart: Long = currentTime

    val done = Source(0 to 100000)
      .map(i => createTestEvent(s"message.kick.$i"))
      .via(resolver.flow())
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
