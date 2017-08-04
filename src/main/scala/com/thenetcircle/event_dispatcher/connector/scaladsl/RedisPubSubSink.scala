package com.thenetcircle.event_dispatcher.connector.scaladsl

import akka.NotUsed
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import akka.util.ByteString
import com.thenetcircle.event_dispatcher.connector.RedisPubSubSinkSettings
import com.thenetcircle.event_dispatcher.connector.adapter.RedisPubSubSinkAdapter
import com.thenetcircle.event_dispatcher.extractor.Extractor
import com.thenetcircle.event_dispatcher.stage.redis.DefaultRedisSinkSettings
import com.thenetcircle.event_dispatcher.stage.redis.scaladsl.RedisSink
import com.thenetcircle.event_dispatcher.Event

object RedisPubSubSink {
  def apply(settings: RedisPubSubSinkSettings): Sink[Event, NotUsed] = {
    val redisSink = RedisSink[ByteString](DefaultRedisSinkSettings(settings.connectionSettings))

    Flow[Event]
      .map(Extractor.deExtract)
      .map(RedisPubSubSinkAdapter.unfit)
      .toMat(redisSink)(Keep.right)
  }
}
