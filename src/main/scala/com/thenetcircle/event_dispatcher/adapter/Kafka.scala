package com.thenetcircle.event_dispatcher.adapter

import akka.Done
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.util.ByteString
import com.thenetcircle.event_dispatcher.{Event, RawEvent, SinkAdapter}
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.Future

final case class KafkaSinkSettings(
    producerSettings: ProducerSettings[Array[Byte], Array[Byte]]
) extends SinkSettings

object KafkaSinkAdapter
    extends SinkAdapter[ProducerRecord[Array[Byte], Array[Byte]]] {

  def unfit(event: RawEvent): ProducerRecord[Array[Byte], Array[Byte]] = {

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
