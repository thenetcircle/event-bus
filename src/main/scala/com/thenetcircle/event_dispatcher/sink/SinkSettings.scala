package com.thenetcircle.event_dispatcher.sink

import akka.kafka.ProducerSettings
import com.thenetcircle.event_dispatcher.driver.{ KafkaKey, KafkaValue }
import com.thenetcircle.event_dispatcher.stage.redis.RedisConnectionSettings

sealed trait SinkSettings

final case class RedisPubSubSinkSettings(
    connectionSettings: RedisConnectionSettings
) extends SinkSettings

final case class KafkaSinkSettings(
    producerSettings: ProducerSettings[KafkaKey, KafkaValue],
    name: String = "DefaultKafkaSink"
) extends SinkSettings
