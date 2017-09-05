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
import akka.kafka.ConsumerMessage.{CommittableOffset, CommittableOffsetBatch}
import akka.kafka.ProducerMessage.Message
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{
  AutoSubscription,
  ConsumerSettings,
  ProducerSettings,
  Subscriptions
}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import akka.stream.{FlowShape, Materializer}
import akka.util.ByteString
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.event_extractor.EventExtractor
import com.thenetcircle.event_bus.pipeline._
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.ExecutionContext

class KafkaPipeline(pipelineSettings: KafkaPipelineSettings)
    extends Pipeline(pipelineSettings) {

  import KafkaPipeline._
  import Pipeline._

  // TODO: maybe we don't need system and materializer, just ExecutionContext is enough, EntryPoints are same
  /** Returns a new [[LeftPort]] of the [[Pipeline]]
    *
    * Which will create a new producer with a new connection to Kafka internally after the port got materialized
    *
    * @param leftPortSettings settings object, needs [[KafkaLeftPortSettings]]
    *
    * @throws IllegalArgumentException
    */
  override def leftPort(leftPortSettings: LeftPortSettings): KafkaLeftPort = {
    require(leftPortSettings.isInstanceOf[KafkaLeftPortSettings],
            "Needs KafkaLeftPortSettings.")

    new KafkaLeftPort(s"$pipelineName-leftport-${leftPortId.getAndIncrement()}",
                      leftPortSettings.asInstanceOf[KafkaLeftPortSettings])
  }

  /** Returns a new [[RightPort]] of the [[Pipeline]]
    *
    * Which will create a new consumer to the kafka Cluster after the port got materialized, It expressed as a Source[Source[Event, _], _]
    * Each (topic, partition) will be presented as a Source[Event, NotUsed]
    * After each [[Event]] got processed, It needs to be commit, There are two ways to do that:
    * 1. Call the committer of the [[Event]] for committing the single [[Event]]
    * 2. Use the committer of the [[RightPort]] (Batched, Recommended)
    *
    * @param rightPortSettings settings object, needs [[KafkaRightPortSettings]]
    *
    * @throws IllegalArgumentException
    */
  override def rightPort(rightPortSettings: RightPortSettings)(
      implicit materializer: Materializer,
      extractor: EventExtractor): KafkaRightPort = {
    require(rightPortSettings.isInstanceOf[KafkaRightPortSettings],
            "Needs KafkaRightPortSettings.")

    new KafkaRightPort(
      s"$pipelineName-rightport-${rightPortId.getAndIncrement()}",
      rightPortSettings.asInstanceOf[KafkaRightPortSettings])
  }

  /** LeftPort Implementation */
  final class KafkaLeftPort(name: String, settings: KafkaLeftPortSettings)
      extends LeftPort {
    override val port: Flow[Event, Event, NotUsed] = {

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
              event
          )
        )
        // TODO: take care of Supervision of mapAsync
        .via(Producer.flow(producerSettings))
        .map(_.message.passThrough)
        .named(name)

    }

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

  /** RightPort Implementation */
  final class KafkaRightPort(name: String, settings: KafkaRightPortSettings)(
      implicit materializer: Materializer,
      extractor: EventExtractor)
      extends RightPort {

    implicit val executionContext: ExecutionContext =
      materializer.executionContext

    override val port: Source[Source[Event, NotUsed], _] = {

      require(settings.topics.isDefined || settings.topicPattern.isDefined,
              "The outlet of KafkaPipeline needs to subscribe topics")

      /** Build ConsumerSettings */
      val kafkaConsumerSettings: ConsumerSettings[Key, Value] = {
        var result =
          pipelineSettings.consumerSettings.withGroupId(settings.groupId)

        settings.dispatcher.foreach(s => result = result.withDispatcher(s))
        settings.properties.foreach(properties =>
          properties foreach {
            case (key, value) =>
              result = result.withProperty(key, value)
        })
        settings.pollInterval.foreach(s => result = result.withPollInterval(s))
        settings.pollTimeout.foreach(s => result = result.withPollTimeout(s))
        settings.stopTimeout.foreach(s => result = result.withStopTimeout(s))
        settings.closeTimeout.foreach(s => result = result.withCloseTimeout(s))
        settings.commitTimeout.foreach(s =>
          result = result.withCommitTimeout(s))
        settings.wakeupTimeout.foreach(s =>
          result = result.withWakeupTimeout(s))
        settings.maxWakeups.foreach(s => result = result.withMaxWakeups(s))

        result
      }

      var subscription: AutoSubscription =
        if (settings.topics.isDefined) {
          Subscriptions.topics(settings.topics.get)
        } else {
          Subscriptions.topicPattern(settings.topicPattern.get)
        }

      // TODO: maybe use one consumer for one partition
      Consumer
        .committablePartitionedSource(kafkaConsumerSettings, subscription)
        .map {
          case (topicPartition, source) =>
            source
              .mapAsync(settings.extractParallelism) { msg =>
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
                    // TODO: replace to the data from key
                    priority = EventPriority(extractedData.priority.id)
                  )
                  event
                    .addContext("kafkaCommittableOffset", committableOffset)
                    .withCommitter(() => committableOffset.commitScaladsl())
              }
        }
        .named(name)
    }

    /** Batch commit flow */
    override lazy val committer: Flow[Event, Event, NotUsed] =
      Flow.fromGraph(GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[Event](2))
        val output    = builder.add(Flow[Event])
        val commitSink =
          Flow[Event]
          // TODO: use generics for event context
            .filter(_.hasContext("kafkaCommittableOffset"))
            .map(_.context("kafkaCommittableOffset")
              .asInstanceOf[CommittableOffset])
            .batch(max = settings.commitBatchMax,
                   first => CommittableOffsetBatch.empty.updated(first)) {
              (batch, elem) =>
                batch.updated(elem)
            }
            .mapAsync(settings.commitParallelism)(_.commitScaladsl())
            .to(Sink.ignore)

        /** --------- work flow --------- */
        broadcast.out(0) ~> commitSink
        broadcast.out(1) ~> output.in

        FlowShape[Event, Event](broadcast.in, output.out)
      })
  }

}

object KafkaPipeline {

  type Key   = Array[Byte]
  type Value = Array[Byte]

  def apply(settings: KafkaPipelineSettings): KafkaPipeline =
    new KafkaPipeline(settings)

}
