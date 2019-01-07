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

import com.thenetcircle.event_bus.IntegrationTestBase
import com.thenetcircle.event_bus.story.tasks.transformations.TNCKafkaTopicResolver

class TNCKafkaTopicResolverTest extends IntegrationTestBase {

  behavior of "TNCKafkaTopicResolver"

  val resolver = new TNCKafkaTopicResolver(zkManager, "event-default")
  resolver.init()

  it should "solve topic correctly" in {

    // ------ test resolving by event name
    resolver
      .resolveEvent(
        createTestEvent("""{ "title": "unknown" }""")
      )
      .metadata
      .topic shouldEqual Some("event-default")

    resolver
      .resolveEvent(
        createTestEvent(
          """{ "title": "membership.test" }"""
        )
      )
      .metadata
      .topic shouldEqual Some(
      "event-membership"
    )

    resolver
      .resolveEvent(
        createTestEvent(
          """{ "title": "user.test" }"""
        )
      )
      .metadata
      .topic shouldEqual Some(
      "event-user"
    )

    // ------ test resolving by event channel
    resolver
      .resolveEvent(
        createTestEvent(
          """{
            |"title": "user.test",
            |"generator": {
            |  "content": "{\"channel\":\"mychannel\"}",
            |  "id": "tnc-event-dispatcher"
            |}}""".stripMargin
        )
      )
      .metadata
      .topic shouldEqual Some("event-user")

    resolver
      .resolveEvent(
        createTestEvent(
          """{
            |"title": "user.test",
            |"generator": {
            |  "content": "{\"channel\":\"mem.normal\"}",
            |  "id": "tnc-event-dispatcher"
            |}}""".stripMargin
        )
      )
      .metadata
      .topic shouldEqual Some("event-membership")

    resolver
      .resolveEvent(
        createTestEvent(
          """{
            |"title": "user.test",
            |"generator": {
            |  "content": "{\"channel\":\"dino\"}",
            |  "id": "tnc-event-dispatcher"
            |}}""".stripMargin
        )
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
