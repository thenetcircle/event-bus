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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{CommittableOffset, CommittableOffsetBatch}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{AutoSubscription, ConsumerSettings, Subscriptions}
import akka.stream.ActorAttributes.supervisionStrategy
import akka.stream.Supervision.resumingDecider
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.extractor.ExtractorFactory
import com.thenetcircle.event_bus.interface.{SourcePlot, SourcePlotBuilder}
import com.thenetcircle.event_bus.plots.kafka.extended.KafkaKeyDeserializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.concurrent.ExecutionContext

case class KafkaSourceSettings(groupId: String,
                               extractParallelism: Int,
                               commitParallelism: Int,
                               commitBatchMax: Int,
                               maxPartitions: Int,
                               consumerSettings: ConsumerSettings[ConsumerKey, ConsumerValue],
                               topics: Option[Set[String]],
                               topicPattern: Option[String])

class KafkaSource(settings: KafkaSourceSettings)(implicit executor: ExecutionContext)
    extends SourcePlot
    with StrictLogging {

  require(
    settings.topics.isDefined || settings.topicPattern.isDefined,
    "The outlet of KafkaPipeline needs to subscribe topics"
  )

  private val subscription: AutoSubscription =
    if (settings.topics.isDefined) {
      Subscriptions.topics(settings.topics.get)
    } else {
      Subscriptions.topicPattern(settings.topicPattern.get)
    }

  private val consumerSettings = settings.consumerSettings.withGroupId(settings.groupId)

  override def getGraph(): Source[Event, NotUsed] = {

    // TODO: maybe use one consumer for one partition
    Consumer
      .committablePartitionedSource(consumerSettings, subscription)
      .flatMapMerge(settings.maxPartitions, _._2)
      .mapAsync(settings.extractParallelism)(msg => {
        val extractor = msg.record
          .key()
          .data
          .map(k => ExtractorFactory.getExtractor(k.eventFormat))
          .getOrElse(ExtractorFactory.defaultExtractor)
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
          Event(
            metadata = extractedData.metadata.withChannel(msg.record.topic()),
            body = extractedData.body,
            context = Map("committableOffset" -> msg.committableOffset)
          )
      }
      .mapMaterializedValue[NotUsed](m => NotUsed)
  }

  // TODO: find a better way of the "kafkaCommittableOffset" part
  override def getCommittingGraph(): Flow[Event, Event, NotUsed] = {
    Flow[Event]
      .batch(
        max = settings.commitBatchMax, {
          case event =>
            val batchCommitter = event.getContext[CommittableOffset]("committableOffset") match {
              case Some(co) => CommittableOffsetBatch.empty.updated(co)
              case None     => CommittableOffsetBatch.empty
            }
            (batchCommitter, List[Event](event))
        }
      ) {
        case ((batchCommitter, eventList), event) =>
          (event.getContext[CommittableOffset]("committableOffset") match {
            case Some(co) => batchCommitter.updated(co)
            case None     => batchCommitter
          }, eventList.+:(event))
      }
      .mapAsync(settings.commitParallelism) {
        case (batchCommitter, eventList) =>
          batchCommitter.commitScaladsl().map(_ => eventList)
      }
      .mapConcat(identity)
      .mapMaterializedValue(m => NotUsed)
  }
}

class KafkaSourceBuilder()(implicit system: ActorSystem, executor: ExecutionContext)
    extends SourcePlotBuilder {

  override def buildFromConfig(config: Config): KafkaSource = {

    val defaultConfig: Config =
      ConfigFactory.parseString("""
          |{
          |  # group-id = ...
          |  # topics = []
          |  # topic-pattern = event-* # supports wildcard
          |  extract-parallelism = 3
          |  commit-parallelism = 3
          |  commit-batch-max = 20
          |  max-partitions = 1000
          |  akka.kafka.consumer {
          |    #use-dispatcher = "akka.kafka.default-dispatcher"
          |    kafka-clients {
          |      client.id = "EventBus-Consumer"
          |    }
          |  }
          |}
        """.stripMargin)

    val mergedConfig = config.withFallback(defaultConfig)

    val consumerConfig = mergedConfig
      .getConfig("akka.kafka.consumer")
      .withFallback(system.settings.config.getConfig("akka.kafka.consumer"))

    val settings = KafkaSourceSettings(
      groupId = config.as[String]("group-id"),
      extractParallelism = config.as[Int]("extract-parallelism"),
      commitParallelism = config.as[Int]("commit-parallelism"),
      commitBatchMax = config.as[Int]("commit-batch-max"),
      maxPartitions = config.as[Int]("max-partitions"),
      consumerSettings = ConsumerSettings[ConsumerKey, ConsumerValue](
        consumerConfig,
        new KafkaKeyDeserializer,
        new ByteArrayDeserializer
      ),
      topics = config.as[Option[Set[String]]]("topics"),
      topicPattern = config.as[Option[String]]("topic-pattern")
    )

    new KafkaSource(settings)

  }

}
