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
import akka.stream.stage.{AsyncCallback, GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{ActorMaterializer, Attributes, Outlet, SourceShape}
import akka.util.ByteString
import redis.RedisPubSub
import redis.api.pubsub.{Message, PMessage}

import scala.collection.mutable
import scala.concurrent.ExecutionContext

final case class IncomingMessage(channel: String, data: ByteString, patternMatched: Option[String] = None)

object RedisSourceStage {
  private val defaultAttributes = Attributes.name("RedisSource")
}

/**
 * Connects to an Redis Pub/Sub server upon materialization and consumes messages from it emitting them
 * into the stream. Each materialized source will create one connection to the server.
 * As soon as an `IncomingMessage` is sent downstream, an ack for it is sent to the server.
 *
 * @param bufferSize The max number of elements to prefetch and buffer at any given time.
 */
class RedisSourceStage(settings: RedisSourceSettings, bufferSize: Int)
    extends GraphStage[SourceShape[IncomingMessage]]
    with RedisConnector { stage =>

  val out = Outlet[IncomingMessage]("RedisSource.out")

  override val shape: SourceShape[IncomingMessage] = SourceShape.of(out)

  override protected def initialAttributes: Attributes = RedisSourceStage.defaultAttributes

  @scala.throws[Exception](classOf[Exception])
  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private val settings: RedisSourceSettings = stage.settings

    private implicit var system: ActorSystem = _

    private implicit var ec: ExecutionContext = _

    private val queue = mutable.Queue[IncomingMessage]()

    private var redisPubSub: RedisPubSub = _

    override def preStart(): Unit = {
      system = materializer.asInstanceOf[ActorMaterializer].system
      ec = materializer.executionContext
      val callback: AsyncCallback[IncomingMessage] =
        getAsyncCallback[IncomingMessage]((msg) => handleIncomingMessage(msg))
      redisPubSub = redisPubSubFrom(settings, handleMessage(callback), handlePMessage(callback))
    }

    override def postStop(): Unit =
      if (redisPubSub ne null) {
        redisPubSub.unsubscribe(settings.channels: _*)
        redisPubSub.punsubscribe(settings.patterns: _*)
        redisPubSub.stop()
        redisPubSub = null
      }

    private def redisPubSubFrom(settings: RedisSourceSettings,
                                onMessage: Message => Unit,
                                onPMessage: PMessage => Unit)(implicit system: ActorSystem): RedisPubSub =
      stage.redisPubSubFrom(settings, onMessage, onPMessage)

    private def handleMessage(callback: AsyncCallback[IncomingMessage])(message: Message): Unit =
      callback.invoke(IncomingMessage(message.channel, message.data))

    private def handlePMessage(callback: AsyncCallback[IncomingMessage])(message: PMessage): Unit =
      callback.invoke(IncomingMessage(message.channel, message.data, Some(message.patternMatched)))

    private def handleIncomingMessage(message: IncomingMessage): Unit =
      if (isAvailable(out)) {
        push(out, message)
      } else {
        if (queue.size + 1 > bufferSize) {
          failStage(new RuntimeException(s"Reached maximum buffer size $bufferSize"))
        } else {
          queue.enqueue(message)
        }
      }

    setHandler(out, new OutHandler {
      @scala.throws[Exception](classOf[Exception])
      def onPull(): Unit =
        if (queue.nonEmpty) {
          push(out, queue.dequeue())
        }
    })

  }
}
