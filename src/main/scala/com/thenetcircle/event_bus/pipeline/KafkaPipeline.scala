package com.thenetcircle.event_bus.pipeline

import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{ CommittableOffset, CommittableOffsetBatch }
import akka.kafka.ProducerMessage.Message
import akka.kafka.scaladsl.{ Consumer, Producer }
import akka.kafka.{ AutoSubscription, ConsumerSettings, ProducerSettings, Subscriptions }
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Keep, MergeHub, Sink, Source }
import akka.stream.{ FlowShape, Graph, Materializer }
import akka.{ Done, NotUsed }
import com.thenetcircle.event_bus.driver.adapter.{ KafkaSinkAdapter, KafkaSourceAdapter }
import com.thenetcircle.event_bus.driver.extractor.Extractor
import com.thenetcircle.event_bus.driver.{ KafkaKey, KafkaValue }
import com.thenetcircle.event_bus.{ Event, EventCommitter, EventFmt }
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, ByteArraySerializer }

import scala.concurrent.Future

case class KafkaPipelineSettings(
    bootstrapServers: String,
    producerClientSettings: Map[String, String] = Map.empty,
    dispatcher: Option[String] = None,
    name: String = "DefaultKafkaPipeline"
) extends PipelineSettings {
  def withName(name: String): KafkaPipelineSettings = copy(name = name)
}

class KafkaPipeline(pipelineSettings: KafkaPipelineSettings)(implicit system: ActorSystem, materializer: Materializer)
    extends Pipeline(pipelineSettings) {

  // Build ProducerSettings
  private var _kps =
    ProducerSettings(system, Some(new ByteArraySerializer), Some(new ByteArraySerializer))
      .withBootstrapServers(pipelineSettings.bootstrapServers)
  for ((k, v) <- pipelineSettings.producerClientSettings) _kps = _kps.withProperty(k, v)
  private val kafkaProducerSettings: ProducerSettings[KafkaKey, KafkaValue] = _kps

  /**
   * If the function `f` throws an exception or if the `Future` is completed
   * with failure and the supervision decision is [[akka.stream.Supervision.Stop]]
   * the stream will be completed with failure.
   *
   * If the function `f` throws an exception or if the `Future` is completed
   * with failure and the supervision decision is [[akka.stream.Supervision.Resume]] or
   * [[akka.stream.Supervision.Restart]] the element is dropped and the stream continues.
   */
  private val producerFlow: Sink[In, Future[Done]] =
    Flow[In]
      .map[(ProducerRecord[KafkaKey, KafkaValue], Option[EventCommitter[_]])](
        event => (KafkaSinkAdapter.unfit(Extractor.deExtract(event)), event.committer)
      )
      .map(data => Message(data._1, data._2))
      .via(Producer.flow(kafkaProducerSettings))
      .filter(m => m.message.passThrough.isDefined)
      .mapAsync(kafkaProducerSettings.parallelism)(_.message.passThrough.get.commit())
      .toMat(Sink.ignore)(Keep.right)

  lazy private val sink =
    MergeHub
      .source[In](perProducerBufferSize = 16)
      .to(producerFlow)
      .run()

  def inlet(): Sink[In, NotUsed] = sink.named(s"$pipelineName-inlet-${inletId.getAndIncrement()}")

  def outlet[Fmt <: EventFmt](
      topics: Option[Set[String]] = None,
      topicPattern: Option[String] = None,
      groupId: String = "DefaultConsumerGroup",
      consumerClientSettings: Map[String, String] = Map.empty
  )(implicit extractor: Extractor[Fmt]): Source[Out, Consumer.Control] = {
    require(topics.isDefined || topicPattern.isDefined, "The outlet of KafkaPipeline needs to subscribe topics")

    // Build ConsumerSettings
    var _kcs = ConsumerSettings(system, Some(new ByteArrayDeserializer), Some(new ByteArrayDeserializer))
      .withBootstrapServers(pipelineSettings.bootstrapServers)
      .withGroupId(groupId)
    for ((k, v) <- consumerClientSettings) _kcs = _kcs.withProperty(k, v)
    val kafkaConsumerSettings: ConsumerSettings[KafkaKey, KafkaValue] = _kcs

    val outletName = s"${pipelineSettings.name}-outlet-${outletId.getAndIncrement()}"

    var subscription: AutoSubscription = if (topics.isDefined) {
      Subscriptions.topics(topics.get)
    } else {
      Subscriptions.topicPattern(topicPattern.get)
    }

    Consumer
      .committableSource(kafkaConsumerSettings, subscription)
      .map[Event](msg => {
        val rawEvent = KafkaSourceAdapter.fit(msg.record).addContext("kafkaCommittableOffset", msg.committableOffset)
        extractor
          .extract(rawEvent)
          .withCommitter(msg.committableOffset.commitScaladsl())
      })
  }

  def batchCommit(parallelism: Int = 3, batchMax: Int = 20): Graph[FlowShape[Out, Out], NotUsed] =
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Out](2))
      val output = builder.add(Flow[Out])
      val commitSink =
        Flow[Out]
          .filter(_.rawEvent.hasContext("kafkaCommittableOffset"))
          .map(_.rawEvent.context("kafkaCommittableOffset").asInstanceOf[CommittableOffset])
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

object KafkaPipeline {

  def apply(pipelineSettings: KafkaPipelineSettings)(implicit system: ActorSystem,
                                                     materializer: Materializer): KafkaPipeline =
    new KafkaPipeline(pipelineSettings)

}
