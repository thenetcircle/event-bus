package com.thenetcircle.event_dispatcher.driver.adapter

import akka.stream.alpakka.amqp.IncomingMessage
import akka.util.ByteString
import com.thenetcircle.event_dispatcher.{ EventSource, RawEvent }

object AMQPSourceAdapter extends SourceAdapter[IncomingMessage] {
  override def fit(message: IncomingMessage): RawEvent =
    RawEvent(
      message.bytes,
      message.envelope.getExchange,
      Map(
        "envelope" -> message.envelope,
        "properties" -> message.properties
      ),
      EventSource.AMQP
    )
}

object AMQPSinkAdapter extends SinkAdapter[ByteString] {
  override def unfit(event: RawEvent): ByteString = event.body
}
