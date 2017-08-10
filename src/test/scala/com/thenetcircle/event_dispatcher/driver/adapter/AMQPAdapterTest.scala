package com.thenetcircle.event_dispatcher.driver.adapter

import akka.stream.alpakka.amqp.IncomingMessage
import akka.util.ByteString
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Envelope
import com.thenetcircle.event_dispatcher.{ EventSource, RawEvent, TestCase }

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
