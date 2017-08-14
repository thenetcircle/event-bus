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

package com.thenetcircle.event_bus.driver.adapter

import akka.util.ByteString
import com.thenetcircle.event_bus.alpakka.redis.{ IncomingMessage, OutgoingMessage }
import com.thenetcircle.event_bus.{ EventEntryPoint$, RawEvent, TestCase }

class RedisPubSubAdapterTest extends TestCase {

  val rawEvent = RawEvent(
    ByteString("test-data"),
    "test-channel",
    Map(
      "patternMatched" -> Some("test-*")
    ),
    EventEntryPoint.Redis
  )

  test("reid pub-sub source adapter") {

    val adapter = RedisPubSubSourceAdapter
    val message =
      IncomingMessage("test-channel", ByteString("test-data"), Some("test-*"))

    adapter.fit(message) shouldEqual rawEvent

  }

  test("redis pub-sub sink adapter") {

    val adapter = RedisPubSubSinkAdapter
    adapter.unfit(rawEvent) shouldEqual OutgoingMessage[ByteString](
      "test-channel",
      ByteString("test-data")
    )

  }

}
