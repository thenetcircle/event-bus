package com.thenetcircle.event_bus.sink

import java.util.concurrent.ConcurrentHashMap

import akka.NotUsed
import akka.kafka.ProducerMessage
import akka.kafka.ProducerMessage.Message
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.{ Flow, Keep }
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.driver.adapter.KafkaSinkAdapter
import com.thenetcircle.event_bus.driver.extractor.Extractor
import com.thenetcircle.event_bus.driver.{ KafkaKey, KafkaValue }
import org.apache.kafka.clients.producer.{ KafkaProducer, ProducerRecord }

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
