package com.thenetcircle.event_dispatcher.sink

import akka.Done
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import com.thenetcircle.event_dispatcher.event.{ Event, EventBody }
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.Future

final case class KafkaSinkSettings(
    producerSettings: ProducerSettings[String, EventBody]
) extends SinkSettings

object KafkaSink {
  def apply(settings: KafkaSinkSettings): Sink[Event, Future[Done]] =
    Flow[Event]
      .map(
        event =>
          new ProducerRecord[String, EventBody](
            event.channel,
            event.uuid,
            event.body
        )
      )
      .toMat(Producer.plainSink(settings.producerSettings))(Keep.right)
}
