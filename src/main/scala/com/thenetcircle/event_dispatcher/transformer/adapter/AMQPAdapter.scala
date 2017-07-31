package com.thenetcircle.event_dispatcher.transformer.adapter

import akka.stream.alpakka.amqp.IncomingMessage
import akka.util.ByteString
import com.thenetcircle.event_dispatcher.RawEvent
import com.thenetcircle.event_dispatcher.transformer.Adapter

class AMQPAdapter extends Adapter[IncomingMessage, ByteString] {

  override def adapt(message: IncomingMessage): RawEvent =
    RawEvent(message.bytes,
             Map(
               "envelope" -> message.envelope,
               "properties" -> message.properties
             ),
             Some(message.envelope.getExchange))

  override def deAdapt(event: RawEvent): ByteString = event.body

}
