package com.thenetcircle.event_dispatcher.connector.scaladsl

import akka.{ Done, NotUsed }
import akka.kafka.ProducerMessage.Message
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.{ Flow, Keep, Sink }
import com.thenetcircle.event_dispatcher.Event
import com.thenetcircle.event_dispatcher.connector.{ KafkaKey, KafkaSinkSettings, KafkaValue }
import com.thenetcircle.event_dispatcher.connector.adapter.KafkaSinkAdapter
import com.thenetcircle.event_dispatcher.extractor.Extractor
import org.apache.kafka.clients.producer.{ KafkaProducer, ProducerRecord }
import java.util.concurrent.ConcurrentHashMap

import akka.kafka.ProducerMessage

object KafkaSink {

  lazy private val producerList = new ConcurrentHashMap[String, KafkaProducer[KafkaKey, KafkaValue]]

  def apply(
      settings: KafkaSinkSettings
  ): Flow[Event, ProducerMessage.Result[KafkaKey, KafkaValue, NotUsed.type], NotUsed] = {

    val producerName = settings.name
    val producerSettings = settings.producerSettings
    val producer: KafkaProducer[KafkaKey, KafkaValue] = if (producerList.containsKey(producerName)) {
      producerList.get(producerName)
    } else {
      producerSettings.createKafkaProducer()
    }

    val kafkaProducerFlow = Flow[ProducerRecord[KafkaKey, KafkaValue]]
      .map(Message(_, NotUsed))
      .via(Producer.flow(settings.producerSettings, producer))

    Flow[Event]
      .map(Extractor.deExtract)
      .map(KafkaSinkAdapter.unfit)
      .viaMat(kafkaProducerFlow)(Keep.left)

  }
}
