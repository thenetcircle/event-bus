package com.thenetcircle.event_dispatcher.connector
import akka.kafka.ProducerSettings
import com.thenetcircle.event_dispatcher.stage.redis.RedisConnectionSettings

sealed trait SourceSettings
sealed trait SinkSettings

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

final case class KafkaSinkSettings(
    producerSettings: ProducerSettings[Array[Byte], Array[Byte]]
) extends SinkSettings
