package com.thenetcircle.event_dispatcher.transformer.adapter

import akka.stream.alpakka.amqp.IncomingMessage
import com.rabbitmq.client.Envelope
import akka.util.ByteString
import com.rabbitmq.client.AMQP.BasicProperties
import com.thenetcircle.event_dispatcher.{TestCase, RawEvent}

class AMQPAdapterTest extends TestCase {

  val adapter = new AMQPAdapter
  val envelope = new Envelope(123, true, "test-channel", "test-*")
  val properties = new BasicProperties()
  val rawEvent = RawEvent(
    ByteString("test-data"),
    Map(
      "envelope" -> envelope,
      "properties" -> properties
    ),
    Some("test-channel")
  )

  test("adapt") {

    val message =
      IncomingMessage(ByteString("test-data"), envelope, properties)

    adapter.adapt(message) shouldEqual rawEvent

  }

  test("deadapt") {

    adapter.deAdapt(rawEvent) shouldEqual ByteString("test-data")

  }

}
