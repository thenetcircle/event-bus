package com.thenetcircle.event_dispatcher.adapter.scaladsl

import akka.Done
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import com.thenetcircle.event_dispatcher.Event
import com.thenetcircle.event_dispatcher.adapter.{ KafkaSinkAdapter, KafkaSinkSettings }
import scala.concurrent.Future

object KafkaSink {
  def apply(settings: KafkaSinkSettings): Sink[Event, Future[Done]] =
    Flow[Event]
      .map(_.rawEvent)
      .map(KafkaSinkAdapter.unfit)
      .toMat(Producer.plainSink(settings.producerSettings))(Keep.right)
}