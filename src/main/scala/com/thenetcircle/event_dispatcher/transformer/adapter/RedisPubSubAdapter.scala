package com.thenetcircle.event_dispatcher.transformer.adapter

import akka.util.ByteString
import com.thenetcircle.event_dispatcher.UnExtractedEvent
import com.thenetcircle.event_dispatcher.stage.redis.{
  IncomingMessage,
  OutgoingMessage
}
import com.thenetcircle.event_dispatcher.transformer.Adapter

class RedisPubSubAdapter
    extends Adapter[IncomingMessage, OutgoingMessage[ByteString]] {

  override def adapt(message: IncomingMessage): UnExtractedEvent =
    UnExtractedEvent(message.data,
                     Map(
                       "patternMatched" -> message.patternMatched
                     ),
                     Some(message.channel))

  override def deAdapt(event: UnExtractedEvent): OutgoingMessage[ByteString] =
    OutgoingMessage[ByteString](event.channel.get, event.body)

}
