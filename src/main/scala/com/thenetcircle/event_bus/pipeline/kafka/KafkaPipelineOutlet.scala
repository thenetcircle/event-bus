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
import akka.kafka.{AutoSubscription, ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import akka.stream.{FlowShape, Materializer}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import akka.util.ByteString
import com.thenetcircle.event_bus.{Event, EventPriority, EventSourceType}
import com.thenetcircle.event_bus.event_extractor.EventExtractor
import com.thenetcircle.event_bus.pipeline.PipelineOutlet

import scala.concurrent.ExecutionContext

/** RightPort Implementation */
private[kafka] final class KafkaPipelineOutlet(
    name: String,
    pipelineSettings: KafkaPipelineSettings,
    settings: KafkaPipelineOutletSettings)(implicit materializer: Materializer,
                                           extractor: EventExtractor)
    extends PipelineOutlet {

  import KafkaPipeline._

  implicit val executionContext: ExecutionContext =
    materializer.executionContext

  override val stream: Source[Source[Event, NotUsed], NotUsed] = {

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
      settings.commitTimeout.foreach(s => result = result.withCommitTimeout(s))
      settings.wakeupTimeout.foreach(s => result = result.withWakeupTimeout(s))
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
      .mapMaterializedValue[NotUsed](m => NotUsed)
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
