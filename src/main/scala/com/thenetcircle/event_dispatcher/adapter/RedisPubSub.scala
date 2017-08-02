package com.thenetcircle.event_dispatcher.adapter

import com.thenetcircle.event_dispatcher.stage.redis.{ IncomingMessage, RedisConnectionSettings }
import com.thenetcircle.event_dispatcher.{ EventFmt, RawEvent, SourceAdapter }

final case class RedisPubSubSourceSettings(
    connectionSettings: RedisConnectionSettings,
    channels: Seq[String] = Seq.empty,
    patterns: Seq[String] = Seq.empty,
    bufferSize: Int = 10,
    dataFormat: EventFmt = EventFmt.Plain
) extends SourceSettings {

  def withBufferSize(bufferSize: Int): RedisPubSubSourceSettings =
    copy(bufferSize = bufferSize)

  def withDataFormat(dataFormat: EventFmt): RedisPubSubSourceSettings =
    copy(dataFormat = dataFormat)

  def withChannels(channels: Seq[String]): RedisPubSubSourceSettings =
    copy(channels = channels)

  def withPatterns(patterns: Seq[String]): RedisPubSubSourceSettings =
    copy(patterns = patterns)

}

object RedisPubSubSourceAdapter extends SourceAdapter[IncomingMessage] {
  def fit(message: IncomingMessage): RawEvent =
    RawEvent(message.data,
             Map(
               "patternMatched" -> message.patternMatched
             ),
             Some(message.channel))
}
