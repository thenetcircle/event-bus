package com.thenetcircle.event_dispatcher

import akka.stream.ClosedShape
import akka.stream.scaladsl.{GraphDSL, RunnableGraph}
import akka.util.ByteString
import com.thenetcircle.event_dispatcher.stage.redis.{
  DefaultRedisSourceSettings,
  IncomingMessage,
  OutgoingMessage,
  RedisConnectionDetails
}
import com.thenetcircle.event_dispatcher.stage.redis.scaladsl.RedisSource
import com.thenetcircle.event_dispatcher.transformer.extractor.ActivityStreamsExtractor
import com.thenetcircle.event_dispatcher.transformer.scaladsl.TransformerFlow

import scala.collection.immutable

object Application extends App {
  implicit val akkaSystem = akka.actor.ActorSystem()

  val stream1 = RunnableGraph
    .fromGraph(GraphDSL.create() { implicit builder =>
      val transformerFlow =
        TransformerFlow[IncomingMessage, OutgoingMessage[ByteString]](
          new ActivityStreamsExtractor).atop()

      val source = RedisSource(DefaultRedisSourceSettings(
                                 RedisConnectionDetails("10.60.1.201", 6379),
                                 patterns = immutable.Seq("test-*")
                               ),
                               10)
        .via()

      ClosedShape
    })
    .named("stream1")
}
