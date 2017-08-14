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

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.thenetcircle.event_bus.alpakka.redis.scaladsl.RedisSource
import com.thenetcircle.event_bus.alpakka.redis.{ DefaultRedisSourceSettings, RedisConnectionSettings }
import com.thenetcircle.event_bus.extractor.Extractor
import com.thenetcircle.event_bus._

import scala.collection.immutable

// TODO move channels and patterns to getSource function
case class RedisPubSubEntryPointSettings(
    name: String,
    connectionSettings: RedisConnectionSettings,
    channels: Seq[String] = Seq.empty,
    patterns: Seq[String] = Seq.empty,
    bufferSize: Int = 10
) extends EntryPointSettings {

  def withBufferSize(bufferSize: Int): RedisPubSubEntryPointSettings =
    copy(bufferSize = bufferSize)

  def withChannels(channels: Seq[String]): RedisPubSubEntryPointSettings =
    copy(channels = channels)

  def withPatterns(patterns: Seq[String]): RedisPubSubEntryPointSettings =
    copy(patterns = patterns)

}

class RedisPubSubEntryPoint(settings: RedisPubSubEntryPointSettings) extends EntryPoint(settings) {

  val entryPointName: String = settings.name

  def port[Fmt <: EventFormat](implicit extractor: Extractor[Fmt]): Source[Event, NotUsed] = {

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
        val (metadata, channel, priority) = extractor.extract(data)

        Event(
          metadata = metadata,
          body = EventBody[EventFormat.DefaultFormat](data),
          channel = channel.getOrElse(msg.channel),
          sourceType = EventSourceType.Redis,
          priority = priority.getOrElse(EventPriority.Normal),
          context = Map("patternMatched" -> msg.patternMatched)
        )

      })
      .named(entryPointName)
  }

}
