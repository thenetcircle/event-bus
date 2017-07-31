package com.thenetcircle.event_dispatcher.transformer.adapter

import akka.util.ByteString
import com.thenetcircle.event_dispatcher.{Event, TestCase, UnExtractedEvent}
import com.thenetcircle.event_dispatcher.stage.redis.{
  IncomingMessage,
  OutgoingMessage
}

class RedisPubSubAdapterTest extends TestCase {

  val adapter = new RedisPubSubAdapter
  val unExtractedEvent = UnExtractedEvent(
    ByteString("test-data"),
    Map(
      "patternMatched" -> Some("test-*")
    ),
    Some("test-channel")
  )

  test("adapt") {

    val message =
      IncomingMessage("test-channel", ByteString("test-data"), Some("test-*"))

    adapter.adapt(message) shouldEqual unExtractedEvent

  }

  test("deadapt") {

    adapter.deAdapt(unExtractedEvent) shouldEqual OutgoingMessage[ByteString](
      "test-channel",
      ByteString("test-data")
    )

  }

}
