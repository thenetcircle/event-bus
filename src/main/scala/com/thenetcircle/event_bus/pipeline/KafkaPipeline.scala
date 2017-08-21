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
import akka.kafka.ConsumerMessage.{CommittableOffset, CommittableOffsetBatch}
import akka.kafka.ProducerMessage.Message
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{
  AutoSubscription,
  ConsumerSettings,
  ProducerSettings,
  Subscriptions
}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{FlowShape, Materializer}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.event_extractor.EventExtractor
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{
  ByteArrayDeserializer,
  ByteArraySerializer
}

import scala.concurrent.{ExecutionContextExecutor, Future}

case class KafkaPipelineSettings(
    name: String,
    bootstrapServers: String,
    producerClientSettings: Map[String, String] = Map.empty,
    consumerClientSettings: Map[String, String] = Map.empty,
    dispatcher: Option[String] = None
) extends PipelineSettings

case class KafkaRightPortSettings(
    groupId: String,
    topics: Option[Set[String]] = None,
    topicPattern: Option[String] = None,
    extractorParallelism: Int = 3
) extends RightPortSettings

class KafkaPipeline(pipelineSettings: KafkaPipelineSettings)(
    implicit system: ActorSystem,
    materializer: Materializer)
    extends Pipeline(pipelineSettings) {

  import KafkaPipeline._

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  /** Get a new inlet of the pipeline,
    * Which will create a new producer internally after the port got materialized
    */
  def leftPort: Sink[Event, Future[Done]] = {
    // Build ProducerSettings
    val kafkaProducerSettings: ProducerSettings[Key, Value] = {
      var _kps =
        ProducerSettings(system,
                         Some(new ByteArraySerializer),
                         Some(new ByteArraySerializer))
          .withBootstrapServers(pipelineSettings.bootstrapServers)
      for ((k, v) <- pipelineSettings.producerClientSettings)
        _kps = _kps.withProperty(k, v)
      _kps
    }

    Flow[Event]
      .map(
        event =>
          Message(
            getProducerRecordFromEvent(event),
            event.committer
        )
      )
      .via(Producer.flow(kafkaProducerSettings))
      .filter(_.message.passThrough.isDefined)
      .mapAsync(kafkaProducerSettings.parallelism)(
        _.message.passThrough.get.commit())
      .toMat(Sink.ignore)(Keep.right)
      .named(s"$pipelineName-leftport-${leftPortId.getAndIncrement()}")
  }

  /** Get a new outlet of the [[KafkaPipeline]],
    * Which will be expressed as a Source[Source[Event, _], _], Each (topic, partition) will be presented as a Source[Event, _]
    * When the [[Source]] got materialized, Internally will create a new consumer to connect to Kafka Cluster to subscribe the topics you appointed
    * After each [[Event]] got processed, It needs to be commit, There are two ways to do that:
    * 1. Call the committer of the [[Event]] for committing the single [[Event]]a
    * 2. Go through [[commit]] flow after the processing stage (Recommended)
    */
  def rightPort(
      portSettings: KafkaRightPortSettings
  )(implicit extractor: EventExtractor)
    : Source[Source[Event, NotUsed], Consumer.Control] = {

    val _topics       = portSettings.topics
    val _topicPattern = portSettings.topicPattern

    require(_topics.isDefined || _topicPattern.isDefined,
            "The outlet of KafkaPipeline needs to subscribe topics")

    // Build ConsumerSettings
    val kafkaConsumerSettings: ConsumerSettings[Key, Value] = {
      var _kcs = ConsumerSettings(system,
                                  Some(new ByteArrayDeserializer),
                                  Some(new ByteArrayDeserializer))
        .withBootstrapServers(pipelineSettings.bootstrapServers)
        .withGroupId(portSettings.groupId)
      for ((k, v) <- pipelineSettings.consumerClientSettings)
        _kcs = _kcs.withProperty(k, v)
      _kcs
    }

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
            .mapAsync(portSettings.extractorParallelism) { msg =>
              val record = msg.record
              extractor
                .extract(ByteString(record.value()))(ec)
                .map(ed => (ed, record.topic(), msg.committableOffset))
            }
            .map {
              case (extractedData, topic, committableOffset) =>
                val event = Event(
                  metadata = extractedData.metadata,
                  body = extractedData.body,
                  channel = extractedData.channel.getOrElse(topic),
                  sourceType = EventSourceType.Kafka,
                  priority =
                    extractedData.priority.getOrElse(EventPriority.Normal)
                )
                event
                  .addContext("kafkaCommittableOffset", committableOffset)
                  .withCommitter(() => committableOffset.commitScaladsl())
            }
      }
      .named(consumerName)
  }

  /** Batch commit flow
    */
  def commit(parallelism: Int = 3,
             batchMax: Int = 20): Flow[Event, Event, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Event](2))
      val output    = builder.add(Flow[Event])
      val commitSink =
        Flow[Event]
          .filter(_.hasContext("kafkaCommittableOffset"))
          .map(
            _.context("kafkaCommittableOffset").asInstanceOf[CommittableOffset])
          .batch(max = batchMax,
                 first => CommittableOffsetBatch.empty.updated(first)) {
            (batch, elem) =>
              batch.updated(elem)
          }
          .mapAsync(parallelism)(_.commitScaladsl())
          .to(Sink.ignore)

      /** --------- work flow --------- */
      broadcast.out(0) ~> commitSink
      broadcast.out(1) ~> output.in

      FlowShape[Event, Event](broadcast.in, output.out)
    })

  private def getProducerRecordFromEvent(
      event: Event): ProducerRecord[Key, Value] = {
    val topic: String   = event.channel
    val timestamp: Long = event.metadata.timestamp
    val key: Key        = getKeyFromEvent(event)
    val value: Value    = event.body.data.toArray

    new ProducerRecord[Key, Value](topic,
                                   null,
                                   timestamp.asInstanceOf[java.lang.Long],
                                   key,
                                   value)
  }

  private def getKeyFromEvent(event: Event): Key =
    ByteString(s"${event.metadata.trigger._1}#${event.metadata.trigger._2}").toArray

}

object KafkaPipeline {

  type Key   = Array[Byte]
  type Value = Array[Byte]

  def apply(pipelineSettings: KafkaPipelineSettings)(
      implicit system: ActorSystem,
      materializer: Materializer): KafkaPipeline =
    new KafkaPipeline(pipelineSettings)

}
