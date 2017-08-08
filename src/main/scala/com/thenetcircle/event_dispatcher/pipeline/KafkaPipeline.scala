package com.thenetcircle.event_dispatcher.pipeline

import akka.NotUsed
import akka.kafka.{ ConsumerSettings, ProducerSettings }
import akka.kafka.ProducerMessage.Message
import akka.kafka.scaladsl.Producer
import akka.stream.Graph
import akka.stream.scaladsl.{ Flow, GraphDSL, Merge, Sink }
import com.thenetcircle.event_dispatcher.Event
import com.thenetcircle.event_dispatcher.driver.adapter.KafkaSinkAdapter
import com.thenetcircle.event_dispatcher.driver.extractor.Extractor
import com.thenetcircle.event_dispatcher.driver.{ KafkaKey, KafkaValue }
import com.thenetcircle.event_dispatcher.sink.KafkaSinkSettings
import com.thenetcircle.event_dispatcher.source.KafkaSourceSettings
import org.apache.kafka.clients.producer.ProducerRecord

case class KafkaPipelineSettings(
    consumerSettings: ConsumerSettings[KafkaKey, KafkaValue],
    producerSettings: ProducerSettings[KafkaKey, KafkaValue],
    sourceSettings: KafkaSourceSettings,
    sinkSettings: KafkaSinkSettings,
    name: String = "KafkaPipeline"
)

class KafkaPipeline(pipelineSettings: KafkaPipelineSettings) {

  def inlet(ports: Int): Graph[PipelineInShape[Event], NotUsed] =
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[Event](ports))
      val decoder = Flow[Event].map(Extractor.deExtract).map(KafkaSinkAdapter.unfit)
      val kafkaProducer = builder.add(
        Flow[ProducerRecord[KafkaKey, KafkaValue]]
          .map(Message(_, NotUsed))
          .via(Producer.flow(pipelineSettings.sinkSettings.producerSettings))
      )

      /* work flow ---------------------------------------- */

      merge ~> decoder ~> kafkaProducer ~> Sink.ignore

      /* -------------------------------------------------- */

      PipelineInShape(merge.inSeq: _*)
    }

  def out(ports: Int) = GraphDSL.create() { implicit builder =>
    }

}
