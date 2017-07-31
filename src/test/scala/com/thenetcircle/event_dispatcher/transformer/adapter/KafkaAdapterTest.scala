package com.thenetcircle.event_dispatcher.transformer.adapter

import akka.util.ByteString
import com.thenetcircle.event_dispatcher.transformer.Adapter.KafkaAdapter
import com.thenetcircle.event_dispatcher.{RawEvent, TestCase}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaAdapterTest extends TestCase {

  val adapter = KafkaAdapter
  val pkey = "test-key".getBytes("UTF-8")
  val pval = "test-data".getBytes("UTF-8")
  val rawEvent = RawEvent(
    ByteString(pval),
    Map(
      "key" -> ByteString(pkey),
      "partition" -> 1,
      "offset" -> 10,
      "timestamp" -> -1
    ),
    Some("test-channel")
  )

  test("adapt") {

    val message =
      new ConsumerRecord[Array[Byte], Array[Byte]]("test-channel",
                                                   1,
                                                   10,
                                                   pkey,
                                                   pval)

    adapter.adapt(message) should equal(rawEvent)

  }

  test("deadapt") {

    val actual = adapter.deAdapt(rawEvent)
    val expected = new ProducerRecord[Array[Byte], Array[Byte]](
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
