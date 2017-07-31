package com.thenetcircle.event_dispatcher.transformer.adapter

import akka.util.ByteString
import com.thenetcircle.event_dispatcher.stage.redis.{
  IncomingMessage,
  OutgoingMessage
}
import com.thenetcircle.event_dispatcher.transformer.Adapter.RedisPubSubAdapter
import com.thenetcircle.event_dispatcher.{RawEvent, TestCase}

class RedisPubSubAdapterTest extends TestCase {

  val adapter = RedisPubSubAdapter
  val rawEvent = RawEvent(
    ByteString("test-data"),
    Map(
      "patternMatched" -> Some("test-*")
    ),
    Some("test-channel")
  )

  test("adapt") {

    val message =
      IncomingMessage("test-channel", ByteString("test-data"), Some("test-*"))

    adapter.adapt(message) shouldEqual rawEvent

  }

  test("deadapt") {

    adapter.deAdapt(rawEvent) shouldEqual OutgoingMessage[ByteString](
      "test-channel",
      ByteString("test-data")
    )

  }

}
