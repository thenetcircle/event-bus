package com.thenetcircle.event_dispatcher.source

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.thenetcircle.event_dispatcher.driver.adapter.RedisPubSubSourceAdapter
import com.thenetcircle.event_dispatcher.driver.extractor.Extractor
import com.thenetcircle.event_dispatcher.stage.redis.DefaultRedisSourceSettings
import com.thenetcircle.event_dispatcher.stage.redis.scaladsl.RedisSource
import com.thenetcircle.event_dispatcher.{ Event, EventFmt }

import scala.collection.immutable

object RedisPubSubSource {
  def apply[Fmt <: EventFmt](
      settings: RedisPubSubSourceSettings
  )(implicit extractor: Extractor[Fmt]): Source[Event, NotUsed] = {

    val redisSource = RedisSource(
      DefaultRedisSourceSettings(
        settings.connectionSettings,
        settings.channels.asInstanceOf[immutable.Seq[String]],
        settings.patterns.asInstanceOf[immutable.Seq[String]]
      ),
      settings.bufferSize
    )

    redisSource
      .map(RedisPubSubSourceAdapter.fit)
      .map(extractor.extract)
  }
}
