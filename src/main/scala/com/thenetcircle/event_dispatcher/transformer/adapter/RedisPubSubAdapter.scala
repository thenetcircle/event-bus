package com.thenetcircle.event_dispatcher.transformer.adapter

import akka.util.ByteString
import com.thenetcircle.event_dispatcher.UnExtractedEvent
import com.thenetcircle.event_dispatcher.stage.redis.IncomingMessage
import com.thenetcircle.event_dispatcher.transformer.Adapter

class RedisPubSubAdapter extends Adapter[IncomingMessage, IncomingMessage] {
  override def adapt(message: IncomingMessage): UnExtractedEvent =
    UnExtractedEvent(message.data.utf8String,
                     Map(
                       "patternMatched" -> message.patternMatched
                     ),
                     Some(message.channel))

  override def deAdapt(event: UnExtractedEvent): IncomingMessage =
    IncomingMessage(
      event.channel.getOrElse(""),
      ByteString(event.body),
      event.context.get("patternMatched").asInstanceOf[Option[String]]
    )
}
