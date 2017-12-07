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
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.Materializer
import akka.stream.Supervision.resumingDecider
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.event_extractor.EventFormat.DefaultFormat
import com.thenetcircle.event_bus.event_extractor.{EventCommitter, EventExtractor, EventSourceType}
import com.thenetcircle.event_bus.pipeline.model.{Pipeline, PipelineOutlet, PipelineOutletSettings}
import com.thenetcircle.event_bus.tracing.{Tracing, TracingSteps}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

/** RightPort Implementation */
private[kafka] final class KafkaPipelineOutlet(val pipeline: KafkaPipeline,
                                               val name: String,
                                               val settings: KafkaPipelineOutletSettings)
    extends PipelineOutlet
    with Tracing
    with StrictLogging {

  require(
    settings.topics.isDefined || settings.topicPattern.isDefined,
    "The outlet of KafkaPipeline needs to subscribe topics"
  )

  /** Build ConsumerSettings */
  private val kafkaConsumerSettings: ConsumerSettings[ConsumerKey, ConsumerValue] = {
    val _settings = pipeline.settings.consumerSettings
      .withGroupId(settings.groupId)

    settings.pollInterval.foreach(_settings.withPollInterval)
    settings.pollTimeout.foreach(_settings.withPollTimeout)
    settings.stopTimeout.foreach(_settings.withStopTimeout)
    settings.closeTimeout.foreach(_settings.withCloseTimeout)
    settings.commitTimeout.foreach(_settings.withCommitTimeout)
    settings.wakeupTimeout.foreach(_settings.withWakeupTimeout)
    settings.maxWakeups.foreach(_settings.withMaxWakeups)

    _settings
  }

  private val subscription: AutoSubscription =
    if (settings.topics.isDefined) {
      Subscriptions.topics(settings.topics.get)
    } else {
      Subscriptions.topicPattern(settings.topicPattern.get)
    }

  override def stream()(
      implicit materializer: Materializer
  ): Source[Source[Event, NotUsed], NotUsed] = {

    implicit val executionContext: ExecutionContext = materializer.executionContext

    // TODO: maybe use one consumer for one partition
    Consumer
      .committablePartitionedSource(kafkaConsumerSettings, subscription)
      .map {
        case (topicPartition, source) =>
          source
            .mapAsync(settings.extractParallelism) { msg =>
              val (tracingId, extractor) =
                msg.record
                  .key()
                  .data
                  .map(
                    k =>
                      (
                        k.tracingId
                          .map(tracer.resumeTracing)
                          .getOrElse(tracer.newTracing()),
                        EventExtractor(k.eventFormat)
                    )
                  )
                  .getOrElse((tracer.newTracing(), EventExtractor(DefaultFormat)))

              tracer.record(tracingId, TracingSteps.PIPELINE_PULLED)

              val msgData = ByteString(msg.record.value())
              val extractFuture = extractor
                .extract(msgData)

              extractFuture.failed.foreach(
                e =>
                  logger.warn(
                    s"Extract message ${msgData.utf8String} from Pipeline failed with Error: ${e.getMessage}"
                )
              )

              extractFuture.map((msg, _, tracingId))
            }
            .withAttributes(supervisionStrategy(resumingDecider))
            .map {
              case (msg, extractedData, tracingId) =>
                tracer.record(tracingId, TracingSteps.EXTRACTED)

                Event(
                  metadata = extractedData.metadata,
                  body = extractedData.body,
                  channel = extractedData.channel.getOrElse(msg.record.topic()),
                  sourceType = EventSourceType.Kafka,
                  tracingId,
                  context = Map("kafkaCommittableOffset" -> msg.committableOffset),
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
      .named(name)
  }

  /** Acknowledges the event to [[Pipeline]]
    *
    * @return the committing akka-stream stage
    */
  override def ackStream(): Sink[Event, NotUsed] = {
    Flow[Event]
      .collect {
        case e if e.hasContext("kafkaCommittableOffset") =>
          e.context("kafkaCommittableOffset")
            .asInstanceOf[CommittableOffset] -> e.tracingId
      }
      .batch(max = settings.commitBatchMax, {
        case (committableOffset, tracingId) =>
          val batch = CommittableOffsetBatch.empty.updated(committableOffset)
          val tracingList = List[Long](tracingId)
          batch -> tracingList
      }) {
        case ((batch, tracingList), (committableOffset, tracingId)) =>
          batch.updated(committableOffset) -> tracingList.+:(tracingId)
      }
      .mapAsync(settings.commitParallelism)(result => {
        result._2.foreach(tracer.record(_, TracingSteps.PIPELINE_COMMITTED))
        result._1.commitScaladsl()
      })
      .to(Sink.ignore)
  }
}

case class KafkaPipelineOutletSettings(groupId: String,
                                       extractParallelism: Int,
                                       commitParallelism: Int,
                                       commitBatchMax: Int,
                                       topics: Option[Set[String]],
                                       topicPattern: Option[String],
                                       pollInterval: Option[FiniteDuration],
                                       pollTimeout: Option[FiniteDuration],
                                       stopTimeout: Option[FiniteDuration],
                                       closeTimeout: Option[FiniteDuration],
                                       commitTimeout: Option[FiniteDuration],
                                       wakeupTimeout: Option[FiniteDuration],
                                       maxWakeups: Option[Int])
    extends PipelineOutletSettings
