package com.thenetcircle.event_dispatcher.stream

import com.thenetcircle.event_dispatcher.TestCase
import com.thenetcircle.event_dispatcher.connector.RedisPubSubSourceSettings
import com.thenetcircle.event_dispatcher.stage.redis.RedisConnectionDetails

class RedisToRedis extends TestCase {

  val settings: RedisPubSubSourceSettings =
    RedisPubSubSourceSettings(RedisConnectionDetails("fat", 3309)).withChannels(Seq("test"))

  test("broadcast from redis") {}

}
