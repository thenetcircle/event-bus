/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Beineng Ma <baineng.ma@gmail.com>
 */

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
import com.thenetcircle.event_bus.{ Event, EventFmt }
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
  private val kafkaProducerSettings: ProducerSettings[KafkaKey, KafkaValue] = {
    var _kps =
      ProducerSettings(system, Some(new ByteArraySerializer), Some(new ByteArraySerializer))
        .withBootstrapServers(pipelineSettings.bootstrapServers)
    for ((k, v) <- pipelineSettings.producerClientSettings) _kps = _kps.withProperty(k, v)
    _kps
  }

  // Predefine producer
  // TODO Supervision
  private val producerFlow: Sink[In, Future[Done]] =
    Flow[In]
      .map(event => (KafkaSinkAdapter.unfit(Extractor.deExtract(event)), event.committer))
      .map(data => Message(data._1, data._2))
      .via(Producer.flow(kafkaProducerSettings))
      .filter(_.message.passThrough.isDefined)
      .mapAsync(kafkaProducerSettings.parallelism)(_.message.passThrough.get.commit())
      .toMat(Sink.ignore)(Keep.right)

  /**
   * Get a new inlet of the pipeline, Which will create a new producer internally
   * One inlet can be shared to different streams as a sink
   *
   * @param perProducerBufferSize Buffer space used per producer. Default value is 16.
   */
  def inlet(perProducerBufferSize: Int = 16): Sink[In, NotUsed] =
    MergeHub
      .source[In](perProducerBufferSize)
      .to(producerFlow)
      .run()
      .named(s"$pipelineName-inlet-${inletId.getAndIncrement()}")

  /**
   * Get outlet of the pipeline
   * While will create a new consumer to connect to kafka subscribing the topics you appointed
   * It will expose multiple sources for each topic and partition pair
   * You need to take care of the sources manually either parallel send to sink or flatten to linear to somewhere else
   */
  def outlet[Fmt <: EventFmt](
      groupId: String,
      topics: Option[Set[String]] = None,
      topicPattern: Option[String] = None,
      consumerClientSettings: Map[String, String] = Map.empty
  )(implicit extractor: Extractor[Fmt]): Source[Source[Event, NotUsed], Consumer.Control] = {
    require(topics.isDefined || topicPattern.isDefined, "The outlet of KafkaPipeline needs to subscribe topics")

    // Build ConsumerSettings
    val kafkaConsumerSettings: ConsumerSettings[KafkaKey, KafkaValue] = {
      var _kcs = ConsumerSettings(system, Some(new ByteArrayDeserializer), Some(new ByteArrayDeserializer))
        .withBootstrapServers(pipelineSettings.bootstrapServers)
        .withGroupId(groupId)
      for ((k, v) <- consumerClientSettings) _kcs = _kcs.withProperty(k, v)
      _kcs
    }

    val outletName = s"${pipelineSettings.name}-outlet-${outletId.getAndIncrement()}"

    var subscription: AutoSubscription = if (topics.isDefined) {
      Subscriptions.topics(topics.get)
    } else {
      Subscriptions.topicPattern(topicPattern.get)
    }

    val consumerName = s"$pipelineName-outlet-${outletId.getAndIncrement()}"

    Consumer
      .committablePartitionedSource(kafkaConsumerSettings, subscription)
      .map {
        case (topicPartition, source) =>
          source
            .map(msg => {
              val rawEvent =
                KafkaSourceAdapter.fit(msg.record).addContext("kafkaCommittableOffset", msg.committableOffset)
              extractor
                .extract(rawEvent)
                .withCommitter(msg.committableOffset.commitScaladsl)
            })
            .named(s"$consumerName-subsource-${topicPartition.topic()}-${topicPartition.partition().toString}")
      }
      .named(consumerName)
  }

  /**
   * After you processed the events from outlet, You need to commit it
   * so the offset on Kafka will be updated on specific topic and partition.
   * You can either use this flow for batchCommit(recommended) or call the committer of the Event to commit only one Event
   */
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
