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

package com.thenetcircle.event_bus.dispatcher.endpoint

import akka.NotUsed
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import akka.util.ByteString
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.alpakka.redis.scaladsl.RedisSink
import com.thenetcircle.event_bus.alpakka.redis.{ DefaultRedisSinkSettings, OutgoingMessage, RedisConnectionSettings }

case class RedisPubSubSinkSettings(
    name: String,
    connectionSettings: RedisConnectionSettings
) extends EndPointSettings

class RedisPubSubEndpoint(settings: RedisPubSubSinkSettings) {

  val endPointName: String = settings.name

  def getSink(): Sink[Event, NotUsed] = {

    val redisSink = RedisSink[ByteString](DefaultRedisSinkSettings(settings.connectionSettings))

    Flow[Event]
      .map(event => {
        OutgoingMessage(event.channel, event.body)
      })
      .toMat(redisSink)(Keep.right)
      .named(endPointName)

  }
}
