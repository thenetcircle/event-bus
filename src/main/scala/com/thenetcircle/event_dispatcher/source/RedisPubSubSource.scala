package com.thenetcircle.event_dispatcher.source
import akka.NotUsed
import akka.stream.scaladsl.Source
import com.thenetcircle.event_dispatcher.event._
import com.thenetcircle.event_dispatcher.stage.redis.{ DefaultRedisSourceSettings, RedisConnectionSettings, Seq => RedisSeq }
import com.thenetcircle.event_dispatcher.stage.redis.scaladsl.RedisSource

import scala.collection.immutable.HashMap

final case class RedisPubSubSourceSettings(
    connectionSettings: RedisConnectionSettings,
    channels: Seq[String] = Seq.empty,
    patterns: Seq[String] = Seq.empty,
    eventExtracterSettings: EventExtracterSettings,
    bufferSize: Int = 10
) extends SourceSettings

object RedisPubSubSource {
  def apply(settings: RedisPubSubSourceSettings): Source[Event, NotUsed] = {

    val redisSource = RedisSource(
      DefaultRedisSourceSettings(
        settings.connectionSettings,
        settings.channels.asInstanceOf[RedisSeq[String]],
        settings.patterns.asInstanceOf[RedisSeq[String]]
      ),
      settings.bufferSize
    )

    redisSource
      .map(
        incomingMessage =>
          UnExtractedEvent(incomingMessage.data,
                           HashMap(
                             "channel" -> incomingMessage.channel,
                             "patternMatched" -> incomingMessage.patternMatched
                           ))
      )
      .via(EventExtractFlow(settings.eventExtracterSettings))

  }
}
