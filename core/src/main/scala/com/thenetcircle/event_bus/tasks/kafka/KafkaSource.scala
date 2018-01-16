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

package com.thenetcircle.event_bus.tasks.kafka

import akka.kafka.ConsumerMessage.CommittableOffset
import akka.kafka.scaladsl.Consumer
import akka.kafka.{AutoSubscription, ConsumerSettings, Subscriptions}
import akka.stream.{KillSwitch, KillSwitches, Materializer}
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.extractor.EventExtractorFactory
import com.thenetcircle.event_bus.interface.{SourceTask, SourceTaskBuilder}
import com.thenetcircle.event_bus.misc.ConfigStringParser
import com.thenetcircle.event_bus.story.TaskRunningContext
import com.thenetcircle.event_bus.tasks.kafka.extended.KafkaKeyDeserializer
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class KafkaSource(val settings: KafkaSourceSettings) extends SourceTask with StrictLogging {

  require(settings.bootstrapServers.nonEmpty, "bootstrap servers is required.")
  require(settings.groupId.nonEmpty, "group id is required.")

  def getSubscription(): AutoSubscription =
    if (settings.subscribedTopics.isLeft) {
      Subscriptions.topics(settings.subscribedTopics.left.get)
    } else {
      Subscriptions.topicPattern(settings.subscribedTopics.right.get)
    }

  def getConsumerSettings()(
      implicit context: TaskRunningContext
  ): ConsumerSettings[ConsumerKey, ConsumerValue] = {
    var _consumerSettings = ConsumerSettings[ConsumerKey, ConsumerValue](
      context.getEnvironment().getConfig(),
      new KafkaKeyDeserializer,
      new ByteArrayDeserializer
    )

    settings.properties.foreach {
      case (_key, _value) => _consumerSettings = _consumerSettings.withProperty(_key, _value)
    }

    _consumerSettings
      .withBootstrapServers(settings.bootstrapServers)
      .withGroupId(settings.groupId)
      .withCommitTimeout(settings.commitTimeout)
      .withProperty("enable.auto.commit", "false")
      .withProperty("client.id", "eventbus-kafkasource")
  }

  override def runWith(
      handler: Flow[Event, (Try[Done], Event), NotUsed]
  )(implicit context: TaskRunningContext): (KillSwitch, Future[Done]) = {

    implicit val materializer: Materializer = context.getMaterializer()
    implicit val executionContext: ExecutionContext = context.getExecutionContext()

    Consumer
      .committablePartitionedSource(getConsumerSettings(), getSubscription())
      .viaMat(KillSwitches.single)(Keep.right)
      .map {
        case (topicPartition, source) =>
          source
            .mapAsync(1)(message => {
              val eventExtractor = message.record
                .key()
                .data
                .map(_key => EventExtractorFactory.getExtractor(_key.eventFormat))
                .getOrElse(EventExtractorFactory.defaultExtractor)

              eventExtractor
                .extract(ByteString(message.record.value()), Some(message.committableOffset))
            })
            .via(handler)
            .mapAsync(1) {
              case (Success(_), event) =>
                event.getPassThrough[CommittableOffset] match {
                  case Some(co) => co.commitScaladsl()
                  case None     => throw new Exception("event passthrough is missed")
                }
              case (Failure(ex), _) => throw ex
            }
            // .withAttributes(supervisionStrategy(resumingDecider))
            .toMat(Sink.ignore)(Keep.right)
            .run()
      }
      .mapAsyncUnordered(settings.maxConcurrentPartitions)(identity)
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }
}

// TODO: change the consumer offset
case class KafkaSourceSettings(bootstrapServers: String,
                               groupId: String,
                               subscribedTopics: Either[Set[String], String],
                               maxConcurrentPartitions: Int = 100,
                               properties: Map[String, String] = Map.empty,
                               commitTimeout: FiniteDuration = 15.seconds)

class KafkaSourceBuilder() extends SourceTaskBuilder {

  override def build(configString: String): KafkaSource = {

    val defaultConfig: Config = ConfigStringParser.convertStringToConfig(
      """
      |{
      |  # "bootstrap-servers": "...",
      |  # "group-id": "...",
      |  # "topics": [],
      |  # "topic-pattern": "event-*", // supports wildcard
      |
      |  "max-concurrent-partitions": 100,
      |
      |  # If offset commit requests are not completed within this timeout
      |  # the returned Future is completed `TimeoutException`.
      |  "commit-timeout": "15s",
      |
      |  # Properties defined by org.apache.kafka.clients.consumer.ConsumerConfig
      |  # can be defined in this configuration section.
      |  "properties": {}
      |}
    """.stripMargin
    )

    val config = ConfigStringParser.convertStringToConfig(configString).withFallback(defaultConfig)

    val subscribedTopics: Either[Set[String], String] = {
      if (config.hasPath("topics"))
        Left(config.as[Set[String]]("topics"))
      else
        Right(config.as[String]("topic-pattern"))
    }

    val settings =
      KafkaSourceSettings(
        config.as[String]("bootstrap-servers"),
        config.as[String]("group-id"),
        subscribedTopics,
        config.as[Int]("max-concurrent-partitions"),
        config.as[Map[String, String]]("properties"),
        config.as[FiniteDuration]("commit-timeout")
      )

    new KafkaSource(settings)

  }

}
