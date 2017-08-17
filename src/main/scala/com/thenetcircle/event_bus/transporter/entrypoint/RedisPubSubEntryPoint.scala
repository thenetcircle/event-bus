/*
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
 *
 * Contributors:
 *     Beineng Ma <baineng.ma@gmail.com>
 */

package com.thenetcircle.event_bus.transporter.entrypoint

import com.thenetcircle.event_bus.alpakka.redis.RedisConnectionSettings

// TODO move channels and patterns to getSource function
case class RedisPubSubEntryPointSettings(
    name: String,
    connectionSettings: RedisConnectionSettings,
    channels: Seq[String] = Seq.empty,
    patterns: Seq[String] = Seq.empty,
    bufferSize: Int = 10
) {

  def withBufferSize(bufferSize: Int): RedisPubSubEntryPointSettings =
    copy(bufferSize = bufferSize)

  def withChannels(channels: Seq[String]): RedisPubSubEntryPointSettings =
    copy(channels = channels)

  def withPatterns(patterns: Seq[String]): RedisPubSubEntryPointSettings =
    copy(patterns = patterns)

}

/*class RedisPubSubEntryPoint(settings: RedisPubSubEntryPointSettings) extends EntryPoint(settings) {

  val entryPointName: String = settings.name

  override val port: Source[Source[Event, NotUsed], NotUsed] = {

    val redisSource = RedisSource(
      DefaultRedisSourceSettings(
        settings.connectionSettings,
        settings.channels.asInstanceOf[immutable.Seq[String]],
        settings.patterns.asInstanceOf[immutable.Seq[String]]
      ),
      settings.bufferSize
    )

    redisSource
      .map(msg => {

        val data: ByteString = msg.data
        val extractedData = extractor.extract(data)

        Event(
          metadata = extractedData.metadata,
          body = EventBody(data, extractor.dataFormat),
          channel = extractedData.channel.getOrElse(msg.channel),
          sourceType = EventSourceType.Redis,
          priority = extractedData.priority.getOrElse(EventPriority.Normal),
          context = Map("patternMatched" -> msg.patternMatched)
        )

      })
      .named(entryPointName)
  }

}*/
