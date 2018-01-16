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

import com.thenetcircle.event_bus.base.AkkaStreamTest

class TopicResolverTransformTest extends AkkaStreamTest {

  behavior of "TopicResolverTransform"

  val resolver = new TopicResolverTransform("event-default")

  it should "solve topic correctly" in {
    /*
    val testEvent1 = createTestEvent("message.send")
    resolver.resolveEvent(testEvent1).metadata.channel shouldEqual Some("topic-message")

    val testEvent2 = createTestEvent("user.profile.kick")
    resolver.resolveEvent(testEvent2).metadata.channel shouldEqual Some("topic-user")

    val testEvent3 = createTestEvent("payment.subscribe")
    resolver.resolveEvent(testEvent3).metadata.channel shouldEqual Some("event-default")
   */
  }

}
