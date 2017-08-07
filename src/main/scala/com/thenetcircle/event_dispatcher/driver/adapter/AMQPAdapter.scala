package com.thenetcircle.event_dispatcher.driver.adapter

import akka.stream.alpakka.amqp.IncomingMessage
import akka.util.ByteString
import com.thenetcircle.event_dispatcher.RawEvent

object AMQPSourceAdapter extends SourceAdapter[IncomingMessage] {
  override def fit(message: IncomingMessage): RawEvent =
    RawEvent(message.bytes,
             Map(
               "envelope" -> message.envelope,
               "properties" -> message.properties
             ),
             Some(message.envelope.getExchange))
}

object AMQPSinkAdapter extends SinkAdapter[ByteString] {
  override def unfit(event: RawEvent): ByteString = event.body
}
