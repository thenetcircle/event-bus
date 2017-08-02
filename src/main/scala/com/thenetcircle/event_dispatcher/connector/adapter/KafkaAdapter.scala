package com.thenetcircle.event_dispatcher.connector.adapter

import akka.util.ByteString
import com.thenetcircle.event_dispatcher.RawEvent
import com.thenetcircle.event_dispatcher.connector.{ KafkaKey, KafkaValue, SinkAdapter, SourceAdapter }
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord

object KafkaSourceAdapter extends SourceAdapter[ConsumerRecord[KafkaKey, KafkaValue]] {
  override def fit(message: ConsumerRecord[KafkaKey, KafkaValue]): RawEvent =
    RawEvent(
      ByteString(message.value()),
      Map("key" -> ByteString(message.key()),
          "offset" -> message.offset(),
          "timestamp" -> message.timestamp(),
          "partition" -> message.partition()),
      channel = Some(message.topic())
    )
}

object KafkaSinkAdapter extends SinkAdapter[ProducerRecord[KafkaKey, KafkaValue]] {
  override def unfit(event: RawEvent): ProducerRecord[KafkaKey, KafkaValue] = {
    val context = event.context

    val topic = event.channel.get
    val value = event.body.toArray
    val partition = context.get("partition") match {
      case Some(p: Int) => p
      case _ => null
    }
    val timestamp = context.get("timestamp") match {
      case Some(t: Long) => t
      case _ => null
    }
    val key = context.get("key") match {
      case Some(k: ByteString) => k.toArray
      case Some(k: String) => k.getBytes("UTF-8")
      case Some(k: KafkaKey) => k
      case _ => null
    }

    new ProducerRecord[KafkaKey, KafkaValue](topic,
                                             partition.asInstanceOf[java.lang.Integer],
                                             timestamp.asInstanceOf[java.lang.Long],
                                             key,
                                             value)
  }

}