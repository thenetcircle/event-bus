package com.thenetcircle.event_dispatcher.transformer
import akka.util.ByteString
import com.thenetcircle.event_dispatcher.TestCase
import com.thenetcircle.event_dispatcher.{ UnExtractedEvent }
import com.thenetcircle.event_dispatcher.stage.redis.IncomingMessage
import com.thenetcircle.event_dispatcher.transformer.adapter.RedisPubSubAdapter

class RedisPubSubAdapterTest extends TestCase
{
  test("adapt") {
    val adapter = new RedisPubSubAdapter
    val message = IncomingMessage("test-channel", ByteString("test data"), Some("test-*"))

    adapter.adapt(message) shouldEqual UnExtractedEvent(
      body = ByteString("test data"),
      context = Map(
        "patternMatched" -> "test-*"
      )
    )
  }
}
