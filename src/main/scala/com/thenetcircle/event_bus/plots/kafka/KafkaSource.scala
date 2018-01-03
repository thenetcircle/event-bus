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

package com.thenetcircle.event_bus.plots.kafka

import akka.kafka.ConsumerMessage.{CommittableOffset, CommittableOffsetBatch}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{AutoSubscription, ConsumerSettings, Subscriptions}
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.Supervision.resumingDecider
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.extractor.EventFormat.DefaultFormat
import com.thenetcircle.event_bus.event.extractor.{EventCommitter, EventExtractor, EventSourceType}
import com.thenetcircle.event_bus.story.interface.ISource
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

case class KafkaSourceSettings(groupId: String,
                               extractParallelism: Int,
                               commitParallelism: Int,
                               commitBatchMax: Int,
                               maxPartitions: Int,
                               consumerSettings: ConsumerSettings[ConsumerKey, ConsumerValue],
                               topics: Option[Set[String]],
                               topicPattern: Option[String])

class KafkaSource(settings: KafkaSourceSettings)(implicit executor: ExecutionContext)
    extends ISource
    with StrictLogging {

  require(settings.topics.isDefined || settings.topicPattern.isDefined,
          "The outlet of KafkaPipeline needs to subscribe topics")

  private val subscription: AutoSubscription =
    if (settings.topics.isDefined) {
      Subscriptions.topics(settings.topics.get)
    } else {
      Subscriptions.topicPattern(settings.topicPattern.get)
    }

  private val consumerSettings = settings.consumerSettings.withGroupId(settings.groupId)

  override def graph: Source[Event, NotUsed] = {

    // TODO: maybe use one consumer for one partition
    Consumer
      .committablePartitionedSource(consumerSettings, subscription)
      .flatMapMerge(settings.maxPartitions, _._2)
      .mapAsync(settings.extractParallelism)(msg => {
        val extractor = msg.record
          .key()
          .data
          .map(k => EventExtractor(k.eventFormat))
          .getOrElse(EventExtractor(DefaultFormat))
        val msgData = ByteString(msg.record.value())
        val extractFuture = extractor
          .extract(msgData)

        extractFuture.failed.foreach(
          e =>
            logger.warn(
              s"Extract message ${msgData.utf8String} from Pipeline failed with Error: ${e.getMessage}"
          )
        )
        extractFuture.map((msg, _))
      })
      .withAttributes(supervisionStrategy(resumingDecider))
      .map {
        case (msg, extractedData) =>
          Event(metadata = extractedData.metadata,
                body = extractedData.body,
                channel = extractedData.channel.getOrElse(msg.record.topic()),
                sourceType = EventSourceType.Kafka,
                Long.MinValue,
                context = Map("kafkaCommittableOffset" -> msg.committableOffset),
                committer = Some(new EventCommitter {
                  override def commit(): Future[Done] = {
                    msg.committableOffset.commitScaladsl()
                  }
                }))
      }
      .mapMaterializedValue[NotUsed](m => NotUsed)
  }

  // TODO: find a better way of the "kafkaCommittableOffset" part
  override def ackGraph: Flow[Event, Event, NotUsed] = {
    Flow[Event]
      .batch(
        max = settings.commitBatchMax, {
          case event =>
            val batchCommitter = if (event.hasContext("kafkaCommittableOffset")) {
              CommittableOffsetBatch.empty
                .updated(event.context("kafkaCommittableOffset").asInstanceOf[CommittableOffset])
            } else {
              CommittableOffsetBatch.empty
            }
            (batchCommitter, List[Event](event))
        }
      ) {
        case ((batchCommitter, eventList), event) =>
          (if (event.hasContext("kafkaCommittableOffset"))
             batchCommitter.updated(
               event.context("kafkaCommittableOffset").asInstanceOf[CommittableOffset]
             )
           else batchCommitter,
           eventList.+:(event))
      }
      .mapAsync(settings.commitParallelism) {
        case (batchCommitter, eventList) =>
          batchCommitter.commitScaladsl().map(_ => eventList)
      }
      .mapConcat(identity)
      .mapMaterializedValue(m => NotUsed)
  }
}
