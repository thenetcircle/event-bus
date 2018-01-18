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

import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{AutoSubscription, ConsumerSettings, Subscriptions}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.extractor.{EventExtractingException, EventExtractorFactory}
import com.thenetcircle.event_bus.helper.ConfigStringParser
import com.thenetcircle.event_bus.interface.{SourceTask, SourceTaskBuilder}
import com.thenetcircle.event_bus.tasks.kafka.extended.KafkaKeyDeserializer
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
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
      implicit runningContext: TaskRunningContext
  ): ConsumerSettings[ConsumerKey, ConsumerValue] = {
    var _consumerSettings = ConsumerSettings[ConsumerKey, ConsumerValue](
      runningContext.getActorSystem(),
      new KafkaKeyDeserializer,
      new ByteArrayDeserializer
    )

    settings.properties.foreach {
      case (_key, _value) => _consumerSettings = _consumerSettings.withProperty(_key, _value)
    }

    settings.useDispatcher.foreach(dp => _consumerSettings = _consumerSettings.withDispatcher(dp))

    val clientId = s"eventbus-${runningContext.getAppContext().getAppName()}"

    _consumerSettings
      .withBootstrapServers(settings.bootstrapServers)
      .withGroupId(settings.groupId)
      .withCommitTimeout(settings.commitTimeout)
      .withProperty("client.id", clientId)
  }

  def extractEventFromMessage(
      message: CommittableMessage[ConsumerKey, ConsumerValue]
  )(implicit executionContext: ExecutionContext): Future[(Try[Done], Event)] = {
    val messageKeyOption = Option(message.record.key())
    val eventExtractor =
      messageKeyOption
        .flatMap(_key => _key.data)
        .map(_key => EventExtractorFactory.getExtractor(_key.eventFormat))
        .getOrElse(EventExtractorFactory.defaultExtractor)
    val eventBody = ByteString(message.record.value())

    eventExtractor
      .extract(eventBody, Some(message.committableOffset))
      .map(event => Success(Done) -> event)
      .recover {
        case ex: EventExtractingException =>
          val dataFormat = eventExtractor.getFormat()
          logger.warn(
            s"The event read from Kafka was extracting failed with format: $dataFormat and error: $ex"
          )
          Failure(ex) -> Event.createEventFromException(eventBody, dataFormat, ex)
      }
  }

  override def runWith(
      handler: Flow[(Try[Done], Event), (Try[Done], Event), NotUsed]
  )(implicit runningContext: TaskRunningContext): (KillSwitch, Future[Done]) = {

    implicit val materializer: Materializer = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    Consumer
      .committablePartitionedSource(getConsumerSettings(), getSubscription())
      .viaMat(KillSwitches.single)(Keep.right)
      .mapAsyncUnordered(settings.maxConcurrentPartitions) {
        case (topicPartition, source) =>
          try {
            source
              .mapAsync(1)(extractEventFromMessage)
              .via(handler)
              .mapAsync(1) {
                case (Success(_), event) =>
                  event.getPassThrough[CommittableOffset] match {
                    case Some(co) =>
                      logger.debug(s"The event is going to commit")
                      co.commitScaladsl() // the commit logic
                    case None =>
                      val errorMessage =
                        s"The event ${event.uniqueName} missed PassThrough[CommittableOffset]"
                      logger.debug(errorMessage)
                      throw new NoSuchElementException(errorMessage)
                  }
                case (Failure(ex), _) =>
                  logger.debug(s"The event reaches the end with processing error $ex")
                  Future.successful(Done)
              }
              .toMat(Sink.ignore)(Keep.right)
              .run()
              .recover {
                case NonFatal(ex) =>
                  logger.warn(
                    s"The substream listening on topicPartition $topicPartition was failed with error: $ex"
                  )
                  Done
              }
              .map(done => {
                logger
                  .info(s"The substream listening on topicPartition $topicPartition was completed.")
                done
              })
          } catch {
            case NonFatal(ex) ⇒
              logger.error(
                s"Could not materialize topic $topicPartition listening stream with error: $ex"
              )
              throw ex
          }
      }
      .toMat(Sink.ignore)(Keep.both)
      .run()
  }
}

// TODO: change the consumer offset
case class KafkaSourceSettings(bootstrapServers: String,
                               groupId: String,
                               subscribedTopics: Either[Set[String], String],
                               maxConcurrentPartitions: Int = 100,
                               commitTimeout: FiniteDuration = 15.seconds,
                               useDispatcher: Option[String] = None,
                               properties: Map[String, String] = Map.empty)

class KafkaSourceBuilder() extends SourceTaskBuilder {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): KafkaSource = {
    val config = ConfigStringParser
      .convertStringToConfig(configString)
      .withFallback(buildingContext.getSystemConfig().getConfig("task.kafka-source"))

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
        config.as[FiniteDuration]("commit-timeout"),
        config.as[Option[String]]("use-dispatcher"),
        config.as[Map[String, String]]("properties")
      )

    new KafkaSource(settings)
  }

}
