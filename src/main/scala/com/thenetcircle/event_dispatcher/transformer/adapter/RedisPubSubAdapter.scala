package com.thenetcircle.event_dispatcher.transformer.adapter

import com.thenetcircle.event_dispatcher.UnExtractedEvent
import com.thenetcircle.event_dispatcher.stage.redis.IncomingMessage
import com.thenetcircle.event_dispatcher.transformer.Adapter

class RedisPubSubAdapter extends Adapter[IncomingMessage]
{
  override def adapt(message: IncomingMessage): UnExtractedEvent =
    UnExtractedEvent(
      body = message.data,
      channel = Some(message.channel),
      context = Map(
        "patternMatched" -> message.patternMatched
      ))

  override def deAdapt(event: UnExtractedEvent): IncomingMessage =
    IncomingMessage(
      event.channel.getOrElse(""),
      event.body,
      event.context.get("patternMatched").asInstanceOf[Option[String]]
    )
}
