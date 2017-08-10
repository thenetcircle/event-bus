/*
 * Copyright 2016 Alvaro Alda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thenetcircle.event_bus.alpakka.redis

/**
 * Internal API
 */
sealed trait RedisConnectorSettings {
  def connectionSettings: RedisConnectionSettings
}

sealed trait RedisSourceSettings extends RedisConnectorSettings {
  def channels: Seq[String]
  def patterns: Seq[String]
}

final case class DefaultRedisSourceSettings(connectionSettings: RedisConnectionSettings,
                                            channels: Seq[String] = Seq.empty,
                                            patterns: Seq[String] = Seq.empty)
    extends RedisSourceSettings {

  @annotation.varargs
  def withChannels(channels: String*) = copy(channels = channels.toList)

  @annotation.varargs
  def withPatterns(patterns: String*) = copy(patterns = patterns.toList)

}

sealed trait RedisSinkSettings extends RedisConnectorSettings

final case class DefaultRedisSinkSettings(connectionSettings: RedisConnectionSettings) extends RedisSinkSettings

/**
 * Only for internal implementations
 */
sealed trait RedisConnectionSettings

final case class RedisConnectionUri(uri: String) extends RedisConnectionSettings

// TODO complete
final case class RedisConnectionDetails(host: String, port: Int, authPassword: Option[String] = None)
    extends RedisConnectionSettings
