package com.thenetcircle.event_bus.sink

import akka.NotUsed
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import akka.util.ByteString
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.driver.adapter.RedisPubSubSinkAdapter
import com.thenetcircle.event_bus.driver.extractor.Extractor
import com.thenetcircle.event_bus.stage.redis.DefaultRedisSinkSettings
import com.thenetcircle.event_bus.stage.redis.scaladsl.RedisSink

object RedisPubSubSink {
  def apply(settings: RedisPubSubSinkSettings): Sink[Event, NotUsed] = {
    val redisSink = RedisSink[ByteString](DefaultRedisSinkSettings(settings.connectionSettings))

    Flow[Event]
      .map(Extractor.deExtract)
      .map(RedisPubSubSinkAdapter.unfit)
      .toMat(redisSink)(Keep.right)
  }
}
