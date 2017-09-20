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
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import akka.stream.{FlowShape, Materializer}
import akka.util.ByteString
import akka.{Done, NotUsed}
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
    implicit materializer: Materializer,
    extractor: EventExtractor)
    extends PipelineOutlet
    with Tracing {

  implicit val executionContext: ExecutionContext =
    materializer.executionContext

  override val stream: Source[Source[Event, NotUsed], NotUsed] = {

    require(
      outletSettings.topics.isDefined || outletSettings.topicPattern.isDefined,
      "The outlet of KafkaPipeline needs to subscribe topics")

    /** Build ConsumerSettings */
    val kafkaConsumerSettings: ConsumerSettings[ConsumerKey, ConsumerValue] = {
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

    var subscription: AutoSubscription =
      if (outletSettings.topics.isDefined) {
        Subscriptions.topics(outletSettings.topics.get)
      } else {
        Subscriptions.topicPattern(outletSettings.topicPattern.get)
      }

    // TODO: maybe use one consumer for one partition
    Consumer
      .committablePartitionedSource(kafkaConsumerSettings, subscription)
      .map {
        case (topicPartition, source) =>
          source
            .mapAsync(outletSettings.extractParallelism) { msg =>
              val tracingId = msg.record
                .key()
                .data
                .map(k => tracer.resumeTracing(k.tracingId))
                .getOrElse(tracer.newTracing())

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
  }

  /** Batch commit flow */
  override lazy val committer: Flow[Event, Event, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val broadcast = builder.add(Broadcast[Event](2))
      val output    = builder.add(Flow[Event])
      val commitSink =
        Flow[Event]
          .collect {
            case e if e.hasContext("kafkaCommittableOffset") =>
              (e.context("kafkaCommittableOffset")
                 .asInstanceOf[CommittableOffset],
               e.tracingId)
          }
          .batch(
            max = outletSettings.commitBatchMax,
            firstElem => {
              val batch       = CommittableOffsetBatch.empty.updated(firstElem._1)
              val tracingList = List[Long](firstElem._2)
              batch -> tracingList
            }
          )((result, elem) =>
            (result._1.updated(elem._1), result._2.+:(elem._2)))
          .mapAsync(outletSettings.commitParallelism)(result => {
            result._2.foreach(tracer.record(_, TracingSteps.PIPELINE_COMMITTED))
            result._1.commitScaladsl()
          })
          .to(Sink.ignore)

      /** --------- work flow --------- */
      broadcast.out(0) ~> commitSink
      broadcast.out(1) ~> output.in

      FlowShape[Event, Event](broadcast.in, output.out)
    })
}
