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
import com.thenetcircle.event_bus.driver.{ KafkaKey, KafkaValue }
import com.thenetcircle.event_bus.{ EventSource, RawEvent, TestCase }
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaAdapterTest extends TestCase {

  val pkey = "test-key".getBytes("UTF-8")
  val pval = "test-data".getBytes("UTF-8")
  val rawEvent = RawEvent(
    ByteString(pval),
    "test-channel",
    Map(
      "key" -> ByteString(pkey),
      "partition" -> 1,
      "offset" -> 10,
      "timestamp" -> -1
    ),
    EventSource.Kafka
  )

  test("kafka source adapter") {

    val adapter = KafkaSourceAdapter
    val message =
      new ConsumerRecord[KafkaKey, KafkaValue]("test-channel", 1, 10, pkey, pval)

    adapter.fit(message) should equal(rawEvent)

  }

  test("kafka sink adapter") {

    val adapter = KafkaSinkAdapter
    val actual = adapter.unfit(rawEvent)
    val expected = new ProducerRecord[KafkaKey, KafkaValue](
      "test-channel",
      1,
      null,
      pkey,
      pval
    )

    actual.topic() shouldEqual expected.topic()
    actual.partition() shouldEqual expected.partition()
    actual.timestamp() shouldEqual expected.timestamp()
    actual.key() shouldBe expected.key()
    actual.value() shouldBe expected.value()

  }

}
