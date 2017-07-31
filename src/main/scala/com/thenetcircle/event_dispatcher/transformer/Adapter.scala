package com.thenetcircle.event_dispatcher.transformer

import akka.stream.alpakka.amqp.{IncomingMessage => AMQPInComingMessage}
import akka.util.ByteString
import com.thenetcircle.event_dispatcher.RawEvent
import com.thenetcircle.event_dispatcher.stage.redis.{
  IncomingMessage,
  OutgoingMessage
}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord

import scala.annotation.implicitNotFound

@implicitNotFound("No adapter found for type ${In},${Out}. ")
trait Adapter[In, Out] {
  def adapt(message: In): RawEvent
  def deAdapt(event: RawEvent): Out
}

object Adapter {

  implicit object AMQPAdapter extends Adapter[AMQPInComingMessage, ByteString] {

    override def adapt(message: AMQPInComingMessage): RawEvent =
      RawEvent(message.bytes,
               Map(
                 "envelope" -> message.envelope,
                 "properties" -> message.properties
               ),
               Some(message.envelope.getExchange))

    override def deAdapt(event: RawEvent): ByteString = event.body

  }

  implicit object KafkaAdapter
      extends Adapter[ConsumerRecord[Array[Byte], Array[Byte]],
                      ProducerRecord[Array[Byte], Array[Byte]]] {

    override def adapt(
        message: ConsumerRecord[Array[Byte], Array[Byte]]): RawEvent =
      RawEvent(
        ByteString(message.value()),
        Map("key" -> ByteString(message.key()),
            "offset" -> message.offset(),
            "timestamp" -> message.timestamp(),
            "partition" -> message.partition()),
        channel = Some(message.topic())
      )

    override def deAdapt(
        event: RawEvent): ProducerRecord[Array[Byte], Array[Byte]] = {

      val context = event.context

      val topic = event.channel.get
      val value = event.body.toArray
      val partition = context.get("partition") match {
        case Some(p: Int) => p
        case _            => null
      }
      val timestamp = context.get("timestamp") match {
        case Some(t: Long) => t
        case _             => null
      }
      val key = context.get("key") match {
        case Some(k: ByteString)  => k.toArray
        case Some(k: String)      => k.getBytes("UTF-8")
        case Some(k: Array[Byte]) => k
        case _                    => null
      }

      new ProducerRecord[Array[Byte], Array[Byte]](
        topic,
        partition.asInstanceOf[java.lang.Integer],
        timestamp.asInstanceOf[java.lang.Long],
        key,
        value)
    }

  }

  implicit object RedisPubSubAdapter
      extends Adapter[IncomingMessage, OutgoingMessage[ByteString]] {

    override def adapt(message: IncomingMessage): RawEvent =
      RawEvent(message.data,
               Map(
                 "patternMatched" -> message.patternMatched
               ),
               Some(message.channel))

    override def deAdapt(event: RawEvent): OutgoingMessage[ByteString] =
      OutgoingMessage[ByteString](event.channel.get, event.body)

  }

}
