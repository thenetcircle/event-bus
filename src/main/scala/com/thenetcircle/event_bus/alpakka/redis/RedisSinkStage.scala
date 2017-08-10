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

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler}
import redis.{ByteStringSerializer, RedisClient}

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

final case class OutgoingMessage[V: ByteStringSerializer](channel: String, value: V)

object RedisSinkStage {

  /**
   * Internal API
   */
  private val defaultAttributes =
    Attributes.name("RedisSink").and(ActorAttributes.dispatcher("akka.stream.default-blocking-io-dispatcher"))

}

/**
 * Connects to a Redis server upon materialization and sends outgoing messages to the server.
 * Each materialized sink will create one connection to the server.
 */
final class RedisSinkStage[V: ByteStringSerializer](settings: RedisSinkSettings)
    extends GraphStage[SinkShape[OutgoingMessage[V]]]
    with RedisConnector { stage =>

  import RedisSinkStage._

  val in = Inlet[OutgoingMessage[V]]("RedisSink.in")

  override def shape: SinkShape[OutgoingMessage[V]] = SinkShape.of(in)

  override protected def initialAttributes: Attributes = defaultAttributes

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private var redisClient: RedisClient = _

    private implicit var system: ActorSystem = _

    private implicit var ec: ExecutionContext = _

    private val settings: RedisSinkSettings = stage.settings

    override def preStart(): Unit = {
      system = materializer.asInstanceOf[ActorMaterializer].system
      ec = materializer.executionContext
      redisClient = redisClientFrom(settings.connectionSettings)
      pull(in)
    }

    override def postStop(): Unit =
      if (redisClient ne null) {
        redisClient.stop()
        redisClient = null
      }

    setHandler(
      in,
      new InHandler {
        override def onPush(): Unit = {
          val msg = grab(in)
          //  TODO add error handling
          val callback = getAsyncCallback[Try[Long]] {
            case Success(_) => pull(in)
            case Failure(ex) => failStage(ex)
          }
          redisClient.publish(msg.channel, msg.value).onComplete(callback.invoke)
        }
      }
    )

  }

  override def toString: String = "RedisSink"

}
