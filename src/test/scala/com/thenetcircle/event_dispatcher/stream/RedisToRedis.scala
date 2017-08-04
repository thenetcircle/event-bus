package com.thenetcircle.event_dispatcher.stream

import akka.stream.scaladsl.Flow
import com.thenetcircle.event_dispatcher.{Event, EventFmt, TestCase}
import com.thenetcircle.event_dispatcher.connector.RedisPubSubSourceSettings
import com.thenetcircle.event_dispatcher.connector.scaladsl.RedisPubSubSource
import com.thenetcircle.event_dispatcher.stage.redis.RedisConnectionDetails

class RedisToRedis extends TestCase {

  val settings: RedisPubSubSourceSettings =
    RedisPubSubSourceSettings(RedisConnectionDetails("fat", 3309)).withChannels(Seq("test"))

  test("broadcast from redis") {

    val source = RedisPubSubSource[EventFmt.ActivityStreams](settings)

    Flow[Event].

  }

}
