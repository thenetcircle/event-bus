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

import akka.stream.alpakka.amqp.IncomingMessage
import akka.util.ByteString
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Envelope
import com.thenetcircle.event_bus.{ EventSource, RawEvent, TestCase }

class AMQPAdapterTest extends TestCase {

  val envelope = new Envelope(123, true, "test-channel", "test-*")
  val properties = new BasicProperties()
  val rawEvent = RawEvent(
    ByteString("test-data"),
    "test-channel",
    Map(
      "envelope" -> envelope,
      "properties" -> properties
    ),
    EventSource.AMQP
  )

  test("adapt") {

    val adapter = AMQPSourceAdapter
    val message =
      IncomingMessage(ByteString("test-data"), envelope, properties)

    adapter.fit(message) shouldEqual rawEvent

  }

  test("deadapt") {

    val adapter = AMQPSinkAdapter
    adapter.unfit(rawEvent) shouldEqual ByteString("test-data")

  }

}
