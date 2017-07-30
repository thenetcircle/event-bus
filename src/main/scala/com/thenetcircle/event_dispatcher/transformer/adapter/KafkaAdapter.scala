package com.thenetcircle.event_dispatcher.transformer.adapter

import akka.util.ByteString
import com.thenetcircle.event_dispatcher.UnExtractedEvent
import com.thenetcircle.event_dispatcher.transformer.Adapter
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaAdapter[K, V] extends Adapter[ProducerRecord[K, V]]
{
  override def adapt(message: ProducerRecord[K, V]): UnExtractedEvent =
    UnExtractedEvent(
      body = ByteString(message.value()),
      context = Map(
        "key"       -> message.key(),
        "partition" -> message.partition().toInt
      ),
      channel = Some(message.topic())
    )

  override def deAdapt(event: UnExtractedEvent): ProducerRecord[K, V] =
  {
    val channel = event.channel.get
    val value   = event.body.asInstanceOf[V]
    val context = event.context

    if (context.isDefinedAt("key")) {
      val key = context("key").asInstanceOf[K]
      if (context.isDefinedAt("partition")) {
        new ProducerRecord[K, V](channel, context("partition").asInstanceOf[Int], key, value)
      }
      else {
        new ProducerRecord[K, V](channel, key, value)
      }
    }
    else {
      new ProducerRecord[K, V](channel, value)
    }
  }
}
