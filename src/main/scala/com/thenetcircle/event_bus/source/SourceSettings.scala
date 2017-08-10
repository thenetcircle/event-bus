package com.thenetcircle.event_bus.source

import akka.kafka.ConsumerSettings
import com.thenetcircle.event_bus.driver.{ KafkaKey, KafkaValue }
import com.thenetcircle.event_bus.alpakka.redis.RedisConnectionSettings

sealed trait SourceSettings

final case class RedisPubSubSourceSettings(
    connectionSettings: RedisConnectionSettings,
    channels: Seq[String] = Seq.empty,
    patterns: Seq[String] = Seq.empty,
    bufferSize: Int = 10
) extends SourceSettings {

  def withBufferSize(bufferSize: Int): RedisPubSubSourceSettings =
    copy(bufferSize = bufferSize)

  def withChannels(channels: Seq[String]): RedisPubSubSourceSettings =
    copy(channels = channels)

  def withPatterns(patterns: Seq[String]): RedisPubSubSourceSettings =
    copy(patterns = patterns)

}

final case class KafkaSourceSettings(
    consumerSettings: ConsumerSettings[KafkaKey, KafkaValue],
    topics: Option[Set[String]] = None,
    topicPattern: Option[String] = None,
    name: String = "DefaultKafkaSource"
) extends SourceSettings
