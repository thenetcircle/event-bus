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
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.event.EventImpl
import com.thenetcircle.event_bus.event.extractor.{EventExtractingException, EventExtractorFactory}
import com.thenetcircle.event_bus.misc.Util
import com.thenetcircle.event_bus.interfaces.EventStatus.{Fail, Norm, Succ, ToFB}
import com.thenetcircle.event_bus.interfaces._
import com.thenetcircle.event_bus.tasks.kafka.extended.KafkaKeyDeserializer
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class KafkaSource(val settings: KafkaSourceSettings) extends SourceTask with StrictLogging {

  require(settings.bootstrapServers.nonEmpty, "bootstrap servers is required.")

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

    val groupId =
      settings.groupId.getOrElse(
        "EB-" + runningContext.getAppContext().getAppName() + "-" + runningContext
          .getStoryRunnerName() + "-" + runningContext.getStoryName()
      )

    _consumerSettings
      .withBootstrapServers(settings.bootstrapServers)
      .withGroupId(groupId)
      .withCommitTimeout(settings.commitTimeout)

    // comment this to prevent the issue javax.management.InstanceAlreadyExistsException: kafka.consumer:type=...
    // .withProperty("client.id", clientId)
  }

  def extractEventFromMessage(
      message: CommittableMessage[ConsumerKey, ConsumerValue]
  )(implicit executionContext: ExecutionContext): Future[(EventStatus, Event)] = {
    val kafkaKeyDataOption = Option(message.record.key()).flatMap(_key => _key.data)
    val messageValue       = message.record.value()
    val kafkaTopic         = message.record.topic()

    val eventExtractor =
      kafkaKeyDataOption
        .map(d => EventExtractorFactory.getExtractor(d.eventFormat))
        .getOrElse(EventExtractorFactory.defaultExtractor)
    val uuidOption = kafkaKeyDataOption.map(_.uuid)

    eventExtractor
      .extract(messageValue, Some(message.committableOffset))
      .map[(EventStatus, Event)](event => {
        var _event = event.withGroup(kafkaTopic)
        if (uuidOption.isDefined) {
          _event = _event.withUUID(uuidOption.get)
        }
        (Norm, _event)
      })
      .recover {
        case ex: EventExtractingException =>
          val eventFormat = eventExtractor.getFormat()
          logger.warn(
            s"The event read from Kafka was extracting failed with format: $eventFormat and error: $ex"
          )
          (
            ToFB(Some(ex)),
            EventImpl
              .createFromFailure(
                ex,
                EventBody(messageValue, eventFormat),
                Some(message.committableOffset)
              )
          )
      }
  }

  var killSwitchOption: Option[KillSwitch] = None

  override def runWith(
      handler: Flow[(EventStatus, Event), (EventStatus, Event), NotUsed]
  )(implicit runningContext: TaskRunningContext): Future[Done] = {

    implicit val materializer: Materializer         = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    val (killSwitch, doneFuture) =
      Consumer
        .committablePartitionedSource(getConsumerSettings(), getSubscription())
        .viaMat(KillSwitches.single)(Keep.right)
        .mapAsyncUnordered(settings.maxConcurrentPartitions) {
          case (topicPartition, source) =>
            try {
              logger.debug(s"A new topicPartition $topicPartition is assigned to be listening")

              source
                .mapAsync(1)(extractEventFromMessage)
                .via(handler)
                .mapAsync(1) {
                  case (_: Succ, event) =>
                    event.getPassThrough[CommittableOffset] match {
                      case Some(co) =>
                        logger.debug(s"The event ${event.uuid} is committing to kafka")
                        co.commitScaladsl() // the commit logic
                      case None =>
                        val errorMessage =
                          s"The event ${event.uuid} missed PassThrough[CommittableOffset]"
                        logger.debug(errorMessage)
                        throw new IllegalStateException(errorMessage)
                    }
                  case (Fail(ex), event) =>
                    logger.debug(s"Event ${event.uuid} reaches the end with error $ex")
                    // complete the stream if failure, before was using Future.successful(Done)
                    throw ex
                }
                .toMat(Sink.ignore)(Keep.right)
                .run()
                .map(done => {
                  logger
                    .info(
                      s"The substream listening on topicPartition $topicPartition was completed."
                    )
                  done
                })
                .recover {
                  case NonFatal(ex) =>
                    logger.warn(
                      s"The substream listening on topicPartition $topicPartition was failed with error: $ex"
                    )
                    Done
                }
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

    killSwitchOption = Some(killSwitch)
    doneFuture
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit =
    killSwitchOption.foreach(k => { k.shutdown(); killSwitchOption = None })
}

// TODO: change the consumer offset
case class KafkaSourceSettings(
    bootstrapServers: String,
    groupId: Option[String],
    subscribedTopics: Either[Set[String], String],
    maxConcurrentPartitions: Int = 100,
    commitTimeout: FiniteDuration = 15.seconds,
    useDispatcher: Option[String] = None,
    properties: Map[String, String] = Map.empty
)

class KafkaSourceBuilder() extends SourceTaskBuilder {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): KafkaSource = {
    val config = Util
      .convertJsonStringToConfig(configString)
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
        config.as[Option[String]]("group-id"),
        subscribedTopics,
        config.as[Int]("max-concurrent-partitions"),
        config.as[FiniteDuration]("commit-timeout"),
        config.as[Option[String]]("use-dispatcher"),
        config.as[Map[String, String]]("properties")
      )

    new KafkaSource(settings)
  }

}
