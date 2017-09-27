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

import akka.kafka.ConsumerMessage.{CommittableOffset, CommittableOffsetBatch}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{AutoSubscription, ConsumerSettings, Subscriptions}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.EventFormat.DefaultFormat
import com.thenetcircle.event_bus.event_extractor.EventExtractor
import com.thenetcircle.event_bus.pipeline.PipelineOutlet
import com.thenetcircle.event_bus.tracing.{Tracing, TracingSteps}
import com.thenetcircle.event_bus.{Event, EventCommitter, EventSourceType}

import scala.concurrent.{ExecutionContext, Future}

/** RightPort Implementation */
private[kafka] final class KafkaPipelineOutlet(
    val pipeline: KafkaPipeline,
    val outletName: String,
    val outletSettings: KafkaPipelineOutletSettings)(
    implicit materializer: Materializer)
    extends PipelineOutlet
    with Tracing {

  require(
    outletSettings.topics.isDefined || outletSettings.topicPattern.isDefined,
    "The outlet of KafkaPipeline needs to subscribe topics")

  implicit val executionContext: ExecutionContext =
    materializer.executionContext

  /** Build ConsumerSettings */
  private val kafkaConsumerSettings
    : ConsumerSettings[ConsumerKey, ConsumerValue] = {
    val _settings = pipeline.pipelineSettings.consumerSettings
      .withGroupId(outletSettings.groupId)

    outletSettings.pollInterval.foreach(_settings.withPollInterval)
    outletSettings.pollTimeout.foreach(_settings.withPollTimeout)
    outletSettings.stopTimeout.foreach(_settings.withStopTimeout)
    outletSettings.closeTimeout.foreach(_settings.withCloseTimeout)
    outletSettings.commitTimeout.foreach(_settings.withCommitTimeout)
    outletSettings.wakeupTimeout.foreach(_settings.withWakeupTimeout)
    outletSettings.maxWakeups.foreach(_settings.withMaxWakeups)

    _settings
  }

  private var subscription: AutoSubscription =
    if (outletSettings.topics.isDefined) {
      Subscriptions.topics(outletSettings.topics.get)
    } else {
      Subscriptions.topicPattern(outletSettings.topicPattern.get)
    }

  override val stream: Source[Source[Event, NotUsed], NotUsed] =
    // TODO: maybe use one consumer for one partition
    Consumer
      .committablePartitionedSource(kafkaConsumerSettings, subscription)
      .map {
        case (topicPartition, source) =>
          source
            .mapAsync(outletSettings.extractParallelism) { msg =>
              val (tracingId, extractor) =
                msg.record
                  .key()
                  .data
                  .map(
                    k =>
                      (k.tracingId
                         .map(tracer.resumeTracing)
                         .getOrElse(tracer.newTracing()),
                       EventExtractor(k.eventFormat)))
                  .getOrElse(
                    (tracer.newTracing(), EventExtractor(DefaultFormat)))

              tracer.record(tracingId, TracingSteps.PIPELINE_PULLED)

              extractor
                .extract(ByteString(msg.record.value()))
                .map((msg, _, tracingId))
            }
            .map {
              case (msg, extractedData, tracingId) =>
                tracer.record(tracingId, TracingSteps.EXTRACTED)

                Event(
                  metadata = extractedData.metadata,
                  body = extractedData.body,
                  channel = extractedData.channel.getOrElse(msg.record.topic()),
                  sourceType = EventSourceType.Kafka,
                  tracingId,
                  context =
                    Map("kafkaCommittableOffset" -> msg.committableOffset),
                  committer = Some(new EventCommitter {
                    override def commit(): Future[Done] = {
                      tracer.record(tracingId, TracingSteps.PIPELINE_COMMITTED)
                      msg.committableOffset.commitScaladsl()
                    }
                  })
                )
            }
      }
      .mapMaterializedValue[NotUsed](m => NotUsed)
      .named(outletName)

  /** Batch commit flow */
  override lazy val committer: Sink[Event, NotUsed] =
    Flow[Event]
      .collect {
        case e if e.hasContext("kafkaCommittableOffset") =>
          e.context("kafkaCommittableOffset")
            .asInstanceOf[CommittableOffset] -> e.tracingId
      }
      .batch(
        max = outletSettings.commitBatchMax, {
          case (committableOffset, tracingId) =>
            val batch       = CommittableOffsetBatch.empty.updated(committableOffset)
            val tracingList = List[Long](tracingId)
            batch -> tracingList
        }
      ) {
        case ((batch, tracingList), (committableOffset, tracingId)) =>
          batch.updated(committableOffset) -> tracingList.+:(tracingId)
      }
      .mapAsync(outletSettings.commitParallelism)(result => {
        result._2.foreach(tracer.record(_, TracingSteps.PIPELINE_COMMITTED))
        result._1.commitScaladsl()
      })
      .to(Sink.ignore)
}
