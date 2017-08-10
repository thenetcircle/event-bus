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

package com.thenetcircle.event_bus.alpakka.redis.scaladsl

import akka.NotUsed
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.thenetcircle.event_bus.alpakka.redis.{OutgoingMessage, RedisSinkSettings, RedisSinkStage}
import redis.ByteStringSerializer

object RedisSink {

  import ByteStringSerializer._

  /**
   * Scala API: Creates an [[RedisSink]] that accepts ByteString elements.
   */
  def simple(settings: RedisSinkSettings): Sink[OutgoingMessage[ByteString], NotUsed] = apply(settings)

  /**
   * Scala API: Creates an [[RedisSink]] that accepts [[OutgoingMessage]] elements.
   */
  def apply[V: ByteStringSerializer](settings: RedisSinkSettings): Sink[OutgoingMessage[V], NotUsed] =
    Sink.fromGraph(new RedisSinkStage(settings))

}
