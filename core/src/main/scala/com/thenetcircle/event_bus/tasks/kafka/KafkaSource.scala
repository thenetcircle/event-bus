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

import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset, CommittableOffsetBatch}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{AutoSubscription, ConsumerSettings, Subscriptions}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.event.EventImpl
import com.thenetcircle.event_bus.event.extractor.{EventExtractingException, EventExtractorFactory}
import com.thenetcircle.event_bus.interfaces.EventStatus.{FAIL, NORM, SuccStatus, TOFB}
import com.thenetcircle.event_bus.interfaces._
import com.thenetcircle.event_bus.misc.{Logging, Util}
import com.thenetcircle.event_bus.tasks.kafka.extended.KafkaKeyDeserializer
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class KafkaSource(val settings: KafkaSourceSettings) extends SourceTask with Logging {

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

    settings.useDispatcher.foreach(v => _consumerSettings = _consumerSettings.withDispatcher(v))
    settings.pollInterval.foreach(v => _consumerSettings = _consumerSettings.withPollInterval(v))
    settings.pollTimeout.foreach(v => _consumerSettings = _consumerSettings.withPollTimeout(v))
    settings.stopTimeout.foreach(v => _consumerSettings = _consumerSettings.withStopTimeout(v))
    settings.closeTimeout.foreach(v => _consumerSettings = _consumerSettings.withCloseTimeout(v))
    settings.commitTimeout.foreach(v => _consumerSettings = _consumerSettings.withCommitTimeout(v))
    settings.commitTimeWarning.foreach(v => _consumerSettings = _consumerSettings.withCommitWarning(v))
    settings.wakeupTimeout.foreach(v => _consumerSettings = _consumerSettings.withWakeupTimeout(v))
    settings.maxWakeups.foreach(v => _consumerSettings = _consumerSettings.withMaxWakeups(v))

    val clientId = s"eventbus-${runningContext.getAppContext().getAppName()}"

    val groupId =
      settings.groupId.getOrElse(
        "event-bus_consumer_" + runningContext.getAppContext().getAppName() +
          "_" + runningContext.getAppContext().getAppEnv() +
          "_" + runningContext.getStoryName()
      )

    _consumerSettings
      .withBootstrapServers(settings.bootstrapServers)
      .withGroupId(groupId)

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
        var eve = event.withTopic(kafkaTopic)
        if (uuidOption.isDefined) {
          eve = eve.withUUID(uuidOption.get)
        }

        val eventBrief = Util.getBriefOfEvent(eve)
        val kafkaBrief =
          s"topic: $kafkaTopic, partition: ${message.record.partition()}, offset: ${message.record
            .offset()}, key: ${Option(message.record.key()).map(_.rawData).getOrElse("")}"
        consumerLogger.info(s"extracted a new event: [$eventBrief] from kafka: [$kafkaBrief]")

        (NORM, eve)
      })
      .recover {
        case ex: EventExtractingException =>
          val eventFormat = eventExtractor.getFormat()
          consumerLogger.warn(
            s"The event read from Kafka was extracting failed with format: $eventFormat and error: $ex"
          )
          (
            TOFB(Some(ex)),
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

    val kafkaConsumerSettings = getConsumerSettings()
    val kafkaSubscription     = getSubscription()
    consumerLogger.info(s"going to subscribe kafka topics: $kafkaSubscription")

    val (killSwitch, doneFuture) =
      Consumer
        .committablePartitionedSource(kafkaConsumerSettings, kafkaSubscription)
        .viaMat(KillSwitches.single)(Keep.right)
        .mapAsyncUnordered(settings.maxConcurrentPartitions) {
          case (topicPartition, source) =>
            try {
              consumerLogger.info(
                s"A new topicPartition $topicPartition was assigned to runner ${runningContext.getStoryRunnerName()}."
              )

              source
                .mapAsync(1)(extractEventFromMessage)
                .via(handler)
                .map {
                  case (_: SuccStatus, event) =>
                    event.getPassThrough[CommittableOffset] match {
                      case Some(co) =>
                        consumerLogger.debug(s"The event ${event.uuid} is adding to the kafka batch committer")
                        // co.commitScaladsl() // the commit logic
                        Success(co)
                      case None =>
                        val errorMessage =
                          s"The event ${event.uuid} missed PassThrough[CommittableOffset]"
                        consumerLogger.error(errorMessage)
                        throw new IllegalStateException(errorMessage)
                    }
                  case (TOFB(exOp), event) =>
                    consumerLogger.error(
                      s"Event ${event.uuid} reaches the end with TOFB status" +
                        exOp.map(e => s" and error ${e.getMessage}").getOrElse("")
                    )
                    throw new RuntimeException("Non handled TOFB status")
                  case (FAIL(ex), event) =>
                    consumerLogger.error(s"Event ${event.uuid} reaches the end with error $ex")
                    // complete the stream if failure, before was using Future.successful(Done)
                    throw ex
                }
                // TODO some test
                .recover {
                  case NonFatal(ex) =>
                    consumerLogger.info(
                      s"The substream listening on topicPartition $topicPartition was failed with error: $ex, " +
                        s"Now it's recovered to be a Failure()"
                    )
                    Failure(ex)
                }
                .collect { case Success(co) => co }
                .batch(max = settings.commitMaxBatches, first => CommittableOffsetBatch.empty.updated(first)) {
                  (batch, elem) =>
                    batch.updated(elem)
                }
                // TODO update parallelism and test order
                .mapAsyncUnordered(1)(_.commitScaladsl())
                .toMat(Sink.ignore)(Keep.right)
                .run()
                .map(done => {
                  consumerLogger
                    .info(
                      s"The substream listening on topicPartition $topicPartition was completed."
                    )
                  done
                })
                .recover { // recover after run, to recover the stream running status
                  case NonFatal(ex) =>
                    consumerLogger.error(
                      s"The substream listening on topicPartition $topicPartition was failed with error: $ex"
                    )
                    Done
                }
            } catch {
              case NonFatal(ex) ⇒
                consumerLogger.error(
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

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    logger.info(s"shutting down kafka-source of story ${runningContext.getStoryName()}.")
    killSwitchOption.foreach(k => {
      k.shutdown(); killSwitchOption = None
    })
  }
}

case class KafkaSourceSettings(
    bootstrapServers: String,
    groupId: Option[String],
    subscribedTopics: Either[Set[String], String],
    maxConcurrentPartitions: Int = 100,
    commitMaxBatches: Int = 20,
    properties: Map[String, String] = Map.empty,
    useDispatcher: Option[String] = None,
    pollInterval: Option[FiniteDuration] = None,
    pollTimeout: Option[FiniteDuration] = None,
    stopTimeout: Option[FiniteDuration] = None,
    closeTimeout: Option[FiniteDuration] = None,
    commitTimeout: Option[FiniteDuration] = None,
    commitTimeWarning: Option[FiniteDuration] = None,
    wakeupTimeout: Option[FiniteDuration] = None,
    maxWakeups: Option[Int] = None
)

class KafkaSourceBuilder() extends SourceTaskBuilder {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): KafkaSource = {
    val config = Util
      .convertJsonStringToConfig(configString)
      .withFallback(buildingContext.getSystemConfig().getConfig("task.kafka-source"))

    val subscribedTopics: Either[Set[String], String] = {
      var topics = Set.empty[String]
      if (config.hasPath("topics")) {
        topics = config.as[Set[String]]("topics")
      }

      if (topics.nonEmpty) {
        Left(topics)
      } else {
        Right(config.as[String]("topic-pattern"))
      }
    }

    val settings =
      KafkaSourceSettings(
        config.as[String]("bootstrap-servers"),
        config.as[Option[String]]("group-id"),
        subscribedTopics,
        config.as[Int]("max-concurrent-partitions"),
        config.as[Int]("commit-max-batches"),
        config.as[Map[String, String]]("properties"),
        config.as[Option[String]]("use-dispatcher"),
        config.as[Option[FiniteDuration]]("poll-interval"),
        config.as[Option[FiniteDuration]]("poll-timeout"),
        config.as[Option[FiniteDuration]]("stop-timeout"),
        config.as[Option[FiniteDuration]]("close-timeout"),
        config.as[Option[FiniteDuration]]("commit-timeout"),
        config.as[Option[FiniteDuration]]("commit-time-warning"),
        config.as[Option[FiniteDuration]]("wakeup-timeout"),
        config.as[Option[Int]]("max-wakeups")
      )

    new KafkaSource(settings)
  }

}
