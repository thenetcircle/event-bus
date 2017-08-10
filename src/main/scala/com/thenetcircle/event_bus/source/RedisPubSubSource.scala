package com.thenetcircle.event_bus.source

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.thenetcircle.event_bus.driver.adapter.RedisPubSubSourceAdapter
import com.thenetcircle.event_bus.driver.extractor.Extractor
import com.thenetcircle.event_bus.stage.redis.DefaultRedisSourceSettings
import com.thenetcircle.event_bus.stage.redis.scaladsl.RedisSource
import com.thenetcircle.event_bus.{ Event, EventFmt }

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
