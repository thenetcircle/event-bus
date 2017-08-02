package com.thenetcircle.event_dispatcher.connector.scaladsl

import akka.NotUsed
import akka.kafka.ConsumerMessage.CommittableOffsetBatch
import akka.kafka.{ AutoSubscription, Subscriptions }
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.{ Flow, Source }
import com.thenetcircle.event_dispatcher.connector.KafkaSourceSettings
import com.thenetcircle.event_dispatcher.extractor.Extractor
import com.thenetcircle.event_dispatcher.{ Event, EventFmt }

object KafkaSource {

  def apply[Fmt <: EventFmt](
      settings: KafkaSourceSettings,
      bizFlow: Flow[Event, _, Any]
  )(implicit extractor: Extractor[Fmt]): Source[Event, NotUsed] = {

    val consumerName = settings.name
    val consumerSettings = settings.consumerSettings

    var subscription: AutoSubscription = if (settings.topics.isDefined) {
      Subscriptions.topics(settings.topics.get)
    } else if (settings.topicPattern.isDefined) {
      Subscriptions.topicPattern(settings.topicPattern.get)
    } else {
      throw new IllegalArgumentException("Kafka source need subscribe topics")
    }

    val consumer = Consumer
      .committableSource(consumerSettings, subscription)
      .map(_.committableOffset)
      .batch(max = 20, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) =>
        batch.updated(elem)
      }
      .named(consumerName)

  }

}
