package com.thenetcircle.event_bus.pipeline

import akka.kafka.{ ConsumerSettings, ProducerSettings }
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.driver.{ KafkaKey, KafkaValue }
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, ByteArraySerializer }

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class KafkaPipelineTest extends AkkaTestCase {

  val producerSettings = ProducerSettings[KafkaKey, KafkaValue](
    _system,
    Some(new ByteArraySerializer),
    Some(new ByteArraySerializer)
  )

  val consumerSettings = ConsumerSettings[KafkaKey, KafkaValue](
    _system,
    Some(new ByteArrayDeserializer),
    Some(new ByteArrayDeserializer)
  )

  val plSettings = KafkaPipelineSettings(consumerSettings, producerSettings)
  val pl = KafkaPipeline(plSettings)

  val plInlet = pl.inlet()

  test("push data in kafkapipeline") {
    Source[Int](1 to 5)
      .map(
        i =>
          Event(
            i.toString,
            System.currentTimeMillis(),
            RawEvent(
              ByteString(s"test-body$i"),
              s"test-channel$i",
              Map("uid" -> i),
              EventSource.Http
            ),
            BizData(),
            EventFmt.Plain()
        )
      )
      .runWith(plInlet)

    Await.ready(_system.whenTerminated, Duration.Inf)
  }

}
