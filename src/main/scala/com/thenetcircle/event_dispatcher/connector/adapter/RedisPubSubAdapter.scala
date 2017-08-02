package com.thenetcircle.event_dispatcher.connector.adapter

import akka.util.ByteString
import com.thenetcircle.event_dispatcher.stage.redis.{ IncomingMessage, OutgoingMessage }
import com.thenetcircle.event_dispatcher.RawEvent
import com.thenetcircle.event_dispatcher.connector.{ SinkAdapter, SourceAdapter }

object RedisPubSubSourceAdapter extends SourceAdapter[IncomingMessage] {
  def fit(message: IncomingMessage): RawEvent =
    RawEvent(message.data,
             Map(
               "patternMatched" -> message.patternMatched
             ),
             Some(message.channel))
}

object RedisPubSubSinkAdapter extends SinkAdapter[OutgoingMessage[ByteString]] {
  override def unfit(event: RawEvent): OutgoingMessage[ByteString] =
    OutgoingMessage[ByteString](event.channel.get, event.body)
}
