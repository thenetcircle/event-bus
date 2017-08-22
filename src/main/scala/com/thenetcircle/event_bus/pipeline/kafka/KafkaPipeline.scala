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

package com.thenetcircle.event_bus.pipeline.kafka

import akka.NotUsed
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
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.event_extractor.EventExtractor
import com.thenetcircle.event_bus.pipeline._
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaPipeline(pipelineSettings: KafkaPipelineSettings) extends Pipeline {
  pipeline =>

  import Pipeline._
  import KafkaPipeline._

  private val pipelineName: String = pipelineSettings.name

  /** Get a new inlet of the [[KafkaPipeline]],
    * Which will create a new producer with a new connection to Kafka internally after the port got materialized
    */
  // TODO: maybe we don't need system and materializer, just ExecutionContext is enough, EntryPoints are same
  def leftPort(settingsBuilder: LeftPortSettingsBuilder)(
      implicit system: ActorSystem,
      materializer: Materializer): Sink[Event, NotUsed] = new KafkaLeftPort()

  private[this] class KafkaLeftPort(settings: KafkaLeftPortSettings)
      extends LeftPort {
    override val port: Sink[Event, NotUsed] = {

      // Combine LeftPortSettings with PipelineSettings
      val producerSettings: ProducerSettings[Key, Value] = {
        var result = pipelineSettings.producerSettings

        settings.properties.foreach(properties =>
          properties foreach {
            case (key, value) =>
              result = result.withProperty(key, value)
        })
        settings.closeTimeout.foreach(s => result = result.withCloseTimeout(s))
        settings.produceParallelism.foreach(s =>
          result = result.withParallelism(s))
        settings.dispatcher.foreach(s => result = result.withDispatcher(s))

        result
      }

      Flow[Event]
        .map(
          event =>
            Message(
              getProducerRecordFromEvent(event),
              event.committer
          )
        )
        // TODO: take care of Supervision of mapAsync
        .via(Producer.flow(producerSettings))
        .filter(_.message.passThrough.isDefined)
        // TODO: also mapAsync here
        .mapAsync(settings.commitParallelism)(
          _.message.passThrough.get.commit())
        .toMat(Sink.ignore)(Keep.left)
        .named(s"$pipelineName-leftport-${leftPortId.getAndIncrement()}")

    }
  }

  /** Get a new outlet of the [[KafkaPipeline]],
    * Which will be expressed as a Source[Source[Event, NotUsed], _], Each (topic, partition) will be presented as a Source[Event, NotUsed]
    * When the outlet got materialized, Internally will create a new consumer to connect to Kafka Cluster to subscribe the topics of settings
    * After each [[Event]] got processed, It needs to be commit, There are two ways to do that:
    * 1. Call the committer of the [[Event]] for committing the single [[Event]]
    * 2. Use [[batchCommit]] of this [[Pipeline]] (Recommended)
    *
    * @throws IllegalArgumentException
    */
  def rightPort(
      settingsBuilder: RightPortSettingsBuilder
  )(implicit system: ActorSystem,
    materializer: Materializer,
    extractor: EventExtractor): Source[Source[Event, NotUsed], _] = {

    require(
      rightPortSettings.topics.isDefined || rightPortSettings.topicPattern.isDefined,
      "The outlet of KafkaPipeline needs to subscribe topics")

    // Build ConsumerSettings
    val kafkaConsumerSettings: ConsumerSettings[Key, Value] = {
      var result =
        pipelineSettings.consumerSettings.withGroupId(rightPortSettings.groupId)

      rightPortSettings.dispatcher.foreach(s =>
        result = result.withDispatcher(s))
      rightPortSettings.properties.foreach(properties =>
        properties foreach {
          case (key, value) =>
            result = result.withProperty(key, value)
      })
      rightPortSettings.pollInterval.foreach(s =>
        result = result.withPollInterval(s))
      rightPortSettings.pollTimeout.foreach(s =>
        result = result.withPollTimeout(s))
      rightPortSettings.stopTimeout.foreach(s =>
        result = result.withStopTimeout(s))
      rightPortSettings.closeTimeout.foreach(s =>
        result = result.withCloseTimeout(s))
      rightPortSettings.commitTimeout.foreach(s =>
        result = result.withCommitTimeout(s))
      rightPortSettings.wakeupTimeout.foreach(s =>
        result = result.withWakeupTimeout(s))
      rightPortSettings.maxWakeups.foreach(s =>
        result = result.withMaxWakeups(s))

      result
    }

    var subscription: AutoSubscription =
      if (rightPortSettings.topics.isDefined) {
        Subscriptions.topics(rightPortSettings.topics.get)
      } else {
        Subscriptions.topicPattern(rightPortSettings.topicPattern.get)
      }

    // TODO: maybe use one consumer for one partition
    Consumer
      .committablePartitionedSource(kafkaConsumerSettings, subscription)
      .map {
        case (topicPartition, source) =>
          source
            .mapAsync(rightPortSettings.extractParallelism) { msg =>
              val record = msg.record
              extractor
                .extract(ByteString(record.value()))
                .map(ed => (ed, record.topic(), msg.committableOffset))
            }
            .map {
              case (extractedData, topic, committableOffset) =>
                val event = Event(
                  metadata = extractedData.metadata,
                  body = extractedData.body,
                  channel = extractedData.channel.getOrElse(topic),
                  sourceType = EventSourceType.Kafka,
                  // TODO: replace with kafka topic key
                  priority = EventPriority(extractedData.priority.id)
                )
                event
                  .addContext("kafkaCommittableOffset", committableOffset)
                  .withCommitter(() => committableOffset.commitScaladsl())
            }
      }
      .named(s"$pipelineName-rightport-${rightPortId.getAndIncrement()}")
  }

  /** Batch commit flow
    */
  def batchCommit(settingsBuilder: BatchCommitSettingsBuilder)
    : Flow[Event, Event, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Event](2))
      val output    = builder.add(Flow[Event])
      val commitSink =
        Flow[Event]
          .filter(_.hasContext("kafkaCommittableOffset"))
          .map(
            _.context("kafkaCommittableOffset").asInstanceOf[CommittableOffset])
          .batch(max = batchCommitSettings.batchMax,
                 first => CommittableOffsetBatch.empty.updated(first)) {
            (batch, elem) =>
              batch.updated(elem)
          }
          .mapAsync(batchCommitSettings.parallelism)(_.commitScaladsl())
          .to(Sink.ignore)

      /** --------- work flow --------- */
      broadcast.out(0) ~> commitSink
      broadcast.out(1) ~> output.in

      FlowShape[Event, Event](broadcast.in, output.out)
    })

  // TODO: manually calculate partition, use key for metadata
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

  def apply(settings: KafkaPipelineSettings): KafkaPipeline =
    new KafkaPipeline(settings)

}
