package com.thenetcircle.event_dispatcher.transformer.adapter

import com.thenetcircle.event_dispatcher.UnExtractedEvent
import com.thenetcircle.event_dispatcher.transformer.Adapter
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaAdapter
    extends Adapter[ConsumerRecord[Array[Byte], String],
                    ProducerRecord[Array[Byte], String]] {
  override def adapt(
      message: ConsumerRecord[Array[Byte], String]): UnExtractedEvent =
    UnExtractedEvent(
      message.value(),
      Map("key" -> message.key(), "partition" -> message.partition()),
      channel = Some(message.topic())
    )

  override def deAdapt(
      event: UnExtractedEvent): ProducerRecord[Array[Byte], String] = {
    val channel = event.channel.get
    val value = event.body
    val context = event.context

    if (context.isDefinedAt("key")) {
      val key = context("key").asInstanceOf[Array[Byte]]
      if (context.isDefinedAt("partition")) {
        new ProducerRecord[Array[Byte], String](
          channel,
          context("partition").asInstanceOf[Int],
          key,
          value)
      } else {
        new ProducerRecord[Array[Byte], String](channel, key, value)
      }
    } else {
      new ProducerRecord[Array[Byte], String](channel, value)
    }
  }
}
