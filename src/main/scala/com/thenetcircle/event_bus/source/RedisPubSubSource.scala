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

package com.thenetcircle.event_bus.source

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.thenetcircle.event_bus.driver.adapter.RedisPubSubSourceAdapter
import com.thenetcircle.event_bus.driver.extractor.Extractor
import com.thenetcircle.event_bus.alpakka.redis.DefaultRedisSourceSettings
import com.thenetcircle.event_bus.alpakka.redis.scaladsl.RedisSource
import com.thenetcircle.event_bus.{ Event, EventFmt }

import scala.collection.immutable

object RedisPubSubSource {
  def apply[Fmt <: EventFmt](
      settings: RedisPubSubSourceSettings
  )(implicit extractor: Extractor[Fmt]): Source[Event, NotUsed] = {

    val redisSource = RedisSource(
      DefaultRedisSourceSettings(
        settings.connectionSettings,
        settings.channels.asInstanceOf[immutable.Seq[String]],
        settings.patterns.asInstanceOf[immutable.Seq[String]]
      ),
      settings.bufferSize
    )

    redisSource
      .map(RedisPubSubSourceAdapter.fit)
      .map(extractor.extract)
  }
}
