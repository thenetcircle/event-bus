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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{ CommittableOffset, CommittableOffsetBatch }
import akka.kafka.ProducerMessage.Message
import akka.kafka.scaladsl.{ Consumer, Producer }
import akka.kafka.{ AutoSubscription, ConsumerSettings, ProducerSettings, Subscriptions }
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Keep, MergeHub, Sink, Source }
import akka.stream.{ FlowShape, Graph, Materializer }
import akka.util.ByteString
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.extractor.Extractor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, ByteArraySerializer }

/**
 * @param perProducerBufferSize Buffer space used per producer. Default value is 16.
 */
case class KafkaPipelineSettings(
    name: String,
    bootstrapServers: String,
    producerClientSettings: Map[String, String] = Map.empty,
    perProducerBufferSize: Int = 16,
    dispatcher: Option[String] = None
) extends PipelineSettings

case class KafkaRightPortSettings(
    groupId: String,
    topics: Option[Set[String]] = None,
    topicPattern: Option[String] = None,
    consumerClientSettings: Map[String, String] = Map.empty
) extends RightPortSettings

class KafkaPipeline(pipelineSettings: KafkaPipelineSettings)(implicit system: ActorSystem, materializer: Materializer)
    extends Pipeline(pipelineSettings) {

  import KafkaPipeline._

  implicit val ec = system.dispatcher

  // Build ProducerSettings
  private val kafkaProducerSettings: ProducerSettings[Key, Value] = {
    var _kps =
      ProducerSettings(system, Some(new ByteArraySerializer), Some(new ByteArraySerializer))
        .withBootstrapServers(pipelineSettings.bootstrapServers)
    for ((k, v) <- pipelineSettings.producerClientSettings) _kps = _kps.withProperty(k, v)
    _kps
  }

  private val kafkaProducer = Flow[Event]
    .map(
      event =>
        Message(
          getProducerRecordFromEvent(event),
          event.committer
      )
    )
    .via(Producer.flow(kafkaProducerSettings))
    .filter(_.message.passThrough.isDefined)
    .mapAsync(kafkaProducerSettings.parallelism)(_.message.passThrough.get.commit())
    .toMat(Sink.ignore)(Keep.right)

  private lazy val _leftPort =
    MergeHub
      .source(pipelineSettings.perProducerBufferSize)
      .toMat(kafkaProducer)(Keep.left)
      .run()

  /**
   * Get a new inlet of the pipeline, Which will create a new producer internally
   * One inlet can be shared to different streams as a sink
   */
  def leftPort: Sink[Event, NotUsed] = _leftPort.named(s"$pipelineName-leftport-${leftPortId.getAndIncrement()}")

  /**
   * Get outlet of the pipeline
   * While will create a new consumer to connect to kafka subscribing the topics you appointed
   * It will expose multiple sources for each topic and partition pair
   * You need to take care of the sources manually either parallel send to sink or flatten to linear to somewhere else
   */
  def rightPort[Fmt <: EventFormat](
      portSettings: KafkaRightPortSettings
  )(implicit extractor: Extractor[Fmt]): Source[Source[Event, NotUsed], Consumer.Control] = {

    val _topics = portSettings.topics
    val _topicPattern = portSettings.topicPattern

    require(_topics.isDefined || _topicPattern.isDefined, "The outlet of KafkaPipeline needs to subscribe topics")

    // Build ConsumerSettings
    val kafkaConsumerSettings: ConsumerSettings[Key, Value] = {
      var _kcs = ConsumerSettings(system, Some(new ByteArrayDeserializer), Some(new ByteArrayDeserializer))
        .withBootstrapServers(pipelineSettings.bootstrapServers)
        .withGroupId(portSettings.groupId)
      for ((k, v) <- portSettings.consumerClientSettings) _kcs = _kcs.withProperty(k, v)
      _kcs
    }

    val outletName = s"${pipelineSettings.name}-outlet-${rightPortId.getAndIncrement()}"

    var subscription: AutoSubscription = if (_topics.isDefined) {
      Subscriptions.topics(_topics.get)
    } else {
      Subscriptions.topicPattern(_topicPattern.get)
    }

    val consumerName = s"$pipelineName-outlet-${rightPortId.getAndIncrement()}"

    Consumer
      .committablePartitionedSource(kafkaConsumerSettings, subscription)
      .map {
        case (topicPartition, source) =>
          source
            .mapAsync(3) { msg =>
              val record = msg.record
              extractor.extract(ByteString(record.value())).map(ed => (ed, record.topic(), msg.committableOffset))
            }
            .map {
              case (extractedData, topic, committableOffset) =>
                val event = Event(
                  metadata = extractedData.metadata,
                  body = extractedData.body,
                  channel = extractedData.channel.getOrElse(topic),
                  sourceType = EventSourceType.Kafka,
                  priority = extractedData.priority.getOrElse(EventPriority.Normal)
                )
                event
                  .addContext("kafkaCommittableOffset", committableOffset)
                  .withCommitter(() => committableOffset.commitScaladsl)
            }
            .named(s"$consumerName-subsource-${topicPartition.topic()}-${topicPartition.partition().toString}")
      }
      .named(consumerName)
  }

  /**
   * After you processed the events from outlet, You need to commit it
   * so the offset on Kafka will be updated on specific topic and partition.
   * You can either use this flow for batchCommit(recommended) or call the committer of the Event to commit only one Event
   */
  def batchCommit(parallelism: Int = 3, batchMax: Int = 20): Graph[FlowShape[Event, Event], NotUsed] =
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Event](2))
      val output = builder.add(Flow[Event])
      val commitSink =
        Flow[Event]
          .filter(_.hasContext("kafkaCommittableOffset"))
          .map(_.context("kafkaCommittableOffset").asInstanceOf[CommittableOffset])
          .batch(max = batchMax, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) =>
            batch.updated(elem)
          }
          .mapAsync(parallelism)(_.commitScaladsl())
          .to(Sink.ignore)

      /** --------- work flow --------- */
      broadcast.out(0) ~> commitSink
      broadcast.out(1) ~> output.in

      FlowShape[Event, Event](broadcast.in, output.out)
    }

  private def getProducerRecordFromEvent(event: Event): ProducerRecord[Key, Value] = {
    val topic: String = event.channel
    val timestamp: Long = event.metadata.timestamp
    val key: Key = getKeyFromEvent(event)
    val value: Value = event.body.data.toArray

    new ProducerRecord[Key, Value](topic, null, timestamp.asInstanceOf[java.lang.Long], key, value)
  }

  private def getKeyFromEvent(event: Event): Key =
    ByteString(s"${event.metadata.trigger._1}#${event.metadata.trigger._2}").toArray

}

object KafkaPipeline {

  type Key = Array[Byte]
  type Value = Array[Byte]

  def apply(pipelineSettings: KafkaPipelineSettings)(implicit system: ActorSystem,
                                                     materializer: Materializer): KafkaPipeline =
    new KafkaPipeline(pipelineSettings)

}
