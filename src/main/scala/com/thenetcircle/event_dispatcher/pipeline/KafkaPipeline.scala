package com.thenetcircle.event_dispatcher.pipeline

import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.kafka.ConsumerMessage.{ CommittableOffset, CommittableOffsetBatch }
import akka.kafka.ProducerMessage.Message
import akka.kafka.scaladsl.{ Consumer, Producer }
import akka.kafka.{ AutoSubscription, ConsumerSettings, ProducerSettings, Subscriptions }
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Keep, MergeHub, Sink, Source }
import akka.stream.{ FlowShape, Graph }
import com.thenetcircle.event_dispatcher.driver.adapter.{ KafkaSinkAdapter, KafkaSourceAdapter }
import com.thenetcircle.event_dispatcher.driver.extractor.Extractor
import com.thenetcircle.event_dispatcher.driver.{ KafkaKey, KafkaValue }
import com.thenetcircle.event_dispatcher.{ Event, EventFmt }

trait PipelineSettings {
  def name: String
  def withName(name: String): PipelineSettings
}

case class KafkaPipelineSettings(
    consumerSettings: ConsumerSettings[KafkaKey, KafkaValue],
    producerSettings: ProducerSettings[KafkaKey, KafkaValue],
    name: String = "DefaultKafkaPipeline"
) extends PipelineSettings {
  def withName(name: String): KafkaPipelineSettings = copy(name = name)
}

class KafkaPipeline(pipelineSettings: KafkaPipelineSettings) {

  type In = Event
  type Out = Event

  private val pipelineName = pipelineSettings.name
  private val inletId = new AtomicInteger(1)
  private val outletId = new AtomicInteger(1)

  private val producerFlow =
    Flow[In]
      .map(Extractor.deExtract)
      .map(KafkaSinkAdapter.unfit)
      .map(Message(_, NotUsed))
      .viaMat(Producer.flow(pipelineSettings.producerSettings))(Keep.left)
      .to(Sink.ignore)

  lazy private val sink =
    MergeHub
      .source[In](perProducerBufferSize = 16)
      .to(producerFlow)
      .run()

  def inlet(): Sink[In, NotUsed] = sink.named(s"$pipelineName-inlet-${inletId.getAndIncrement()}")

  def outlet[Fmt <: EventFmt](topics: Option[Set[String]] = None, topicPattern: Option[String] = None)(
      implicit extractor: Extractor[Fmt]
  ): Source[Out, Consumer.Control] = {
    require(topics.isDefined || topicPattern.isDefined, "The outlet of KafkaPipeline needs to subscribe topics")

    val outletName = s"${pipelineSettings.name}-outlet-${outletId.getAndIncrement()}"

    var subscription: AutoSubscription = if (topics.isDefined) {
      Subscriptions.topics(topics.get)
    } else {
      Subscriptions.topicPattern(topicPattern.get)
    }

    Consumer
      .committableSource(pipelineSettings.consumerSettings, subscription)
      .map(msg => {
        KafkaSourceAdapter.fit(msg.record).addContext("committableOffset", msg.committableOffset)
      })
      .map(extractor.extract)
  }

  def batchCommit(parallelism: Int = 3, batchMax: Int = 20): Graph[FlowShape[Out, Out], NotUsed] =
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Out](2))
      val output = builder.add(Flow[Out])
      val commitSink =
        Flow[Out]
          .filter(_.rawEvent.hasContext("committableOffset"))
          .map(_.rawEvent.context("committableOffset").asInstanceOf[CommittableOffset])
          .batch(max = batchMax, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) =>
            batch.updated(elem)
          }
          .mapAsync(parallelism)(_.commitScaladsl())
          .to(Sink.ignore)

      /** --------- work flow --------- */
      broadcast.out(0) ~> commitSink
      broadcast.out(1) ~> output.in

      FlowShape[Out, Out](broadcast.in, output.out)
    }

}
