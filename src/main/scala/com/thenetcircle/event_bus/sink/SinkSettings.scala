package com.thenetcircle.event_bus.sink

import akka.kafka.ProducerSettings
import com.thenetcircle.event_bus.driver.{ KafkaKey, KafkaValue }
import com.thenetcircle.event_bus.stage.redis.RedisConnectionSettings

sealed trait SinkSettings

final case class RedisPubSubSinkSettings(
    connectionSettings: RedisConnectionSettings
) extends SinkSettings

final case class KafkaSinkSettings(
    producerSettings: ProducerSettings[KafkaKey, KafkaValue],
    name: String = "DefaultKafkaSink"
) extends SinkSettings
