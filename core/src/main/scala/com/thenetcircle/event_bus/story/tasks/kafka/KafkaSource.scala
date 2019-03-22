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

package com.thenetcircle.event_bus.story.tasks.kafka

import akka.Done
import akka.kafka.ConsumerMessage.{CommittableMessage, CommittableOffset, CommittableOffsetBatch}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{AutoSubscription, ConsumerSettings, Subscriptions}
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink}
import com.thenetcircle.event_bus.AppContext
import com.thenetcircle.event_bus.event.EventStatus._
import com.thenetcircle.event_bus.event._
import com.thenetcircle.event_bus.event.extractor.{EventExtractingException, EventExtractorFactory}
import com.thenetcircle.event_bus.story.interfaces._
import com.thenetcircle.event_bus.story.tasks.kafka.KafkaSource.CommittableException
import com.thenetcircle.event_bus.story.tasks.kafka.extended.KafkaKeyDeserializer
import com.thenetcircle.event_bus.story.{Payload, StoryMat, TaskRunningContext}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.clients.consumer.RetriableCommitFailedException
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class KafkaSource(val settings: KafkaSourceSettings) extends ISource with ITaskLogging {

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
    var consumerSettings = ConsumerSettings[ConsumerKey, ConsumerValue](
      runningContext.getActorSystem(),
      new KafkaKeyDeserializer,
      new ByteArrayDeserializer
    )

    val clientSettings = settings.clientSettings

    clientSettings.properties.foreach {
      case (_key, _value) => consumerSettings = consumerSettings.withProperty(_key, _value)
    }

    clientSettings.useDispatcher.foreach(v => consumerSettings = consumerSettings.withDispatcher(v))
    clientSettings.pollInterval.foreach(v => consumerSettings = consumerSettings.withPollInterval(v))
    clientSettings.pollTimeout.foreach(v => consumerSettings = consumerSettings.withPollTimeout(v))
    clientSettings.stopTimeout.foreach(v => consumerSettings = consumerSettings.withStopTimeout(v))
    clientSettings.closeTimeout.foreach(v => consumerSettings = consumerSettings.withCloseTimeout(v))
    clientSettings.commitTimeout.foreach(v => consumerSettings = consumerSettings.withCommitTimeout(v))
    clientSettings.commitTimeWarning.foreach(v => consumerSettings = consumerSettings.withCommitWarning(v))
    clientSettings.wakeupTimeout.foreach(v => consumerSettings = consumerSettings.withWakeupTimeout(v))
    clientSettings.maxWakeups.foreach(v => consumerSettings = consumerSettings.withMaxWakeups(v))
    clientSettings.waitClosePartition.foreach(v => consumerSettings = consumerSettings.withWaitClosePartition(v))
    clientSettings.wakeupDebug.foreach(v => consumerSettings = consumerSettings.withWakeupDebug(v))

    val clientId = s"eventbus-${runningContext.getAppContext().getAppName()}"

    val groupId =
      settings.groupId.getOrElse(
        "event-bus_consumer_" + runningContext.getAppContext().getAppName() +
          "_" + runningContext.getAppContext().getAppEnv() +
          "_" + getStoryName()
      )

    consumerSettings
      .withBootstrapServers(settings.bootstrapServers)
      .withGroupId(groupId)

    // comment this to prevent the issue javax.management.InstanceAlreadyExistsException: kafka.consumer:type=...
    // .withProperty("client.id", clientId)
  }

  def extractEventFromMessage(
      message: CommittableMessage[ConsumerKey, ConsumerValue]
  )(implicit executionContext: ExecutionContext): Future[Payload] = {
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
      .map[Payload](event => {
        // var eve = event.withTopic(kafkaTopic)
        var eve = event
        if (uuidOption.isDefined) {
          eve = eve.withUUID(uuidOption.get)
        }

        val kafkaBrief =
          s"topic: $kafkaTopic, partition: ${message.record.partition()}, offset: ${message.record
            .offset()}, key: ${Option(message.record.key()).map(_.rawData).getOrElse("")}"
        taskLogger
          .info(s"Extracted a new event from Kafka, event: [${eve.summary}], Kafka: [$kafkaBrief]")

        (NORMAL, eve)
      })
      .recover {
        case ex: EventExtractingException =>
          val eventFormat = eventExtractor.getFormat()
          taskLogger.warn(
            s"A event read from Kafka was extracting failed with format: $eventFormat and error: $ex"
          )
          (
            SKIPPING,
            Event.fromException(
              ex,
              EventBody(messageValue, eventFormat),
              Some(message.committableOffset)
            )
          )
      }
  }

  var killSwitchOption: Option[KillSwitch] = None

  override def run(
      storyFlow: Flow[Payload, Payload, StoryMat]
  )(implicit runningContext: TaskRunningContext): Future[Done] = {

    implicit val materializer: Materializer         = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    val kafkaConsumerSettings = getConsumerSettings()
    val kafkaSubscription     = getSubscription()
    taskLogger.info(s"Going to subscribe kafka topics: $kafkaSubscription")

    val (killSwitch, doneFuture) =
      Consumer
        .committablePartitionedSource(kafkaConsumerSettings, kafkaSubscription)
        .viaMat(KillSwitches.single)(Keep.right)
        .mapAsyncUnordered(settings.maxConcurrentPartitions) {
          case (topicPartition, source) =>
            try {
              taskLogger.info(
                s"A new topicPartition $topicPartition has been assigned to story ${getStoryName()}."
              )

              source
                .mapAsync(1)(extractEventFromMessage)
                .via(storyFlow)
                .map {
                  case (eventStatus: SuccStatus, event) =>
                    event.getPassThrough[CommittableOffset] match {
                      case Some(co) =>
                        taskLogger.info(
                          s"A event is going to be committed to Kafka with $eventStatus status,  ${event.summary}, With offset $co"
                        )
                        // co.commitScaladsl() // the commit logic
                        Success(co)
                      case None =>
                        val errorMessage =
                          s"A event missed PassThrough[CommittableOffset] with $eventStatus status, ${event.summary}"
                        taskLogger.error(errorMessage)
                        throw new IllegalStateException(errorMessage)
                    }

                  case (STAGING(exOp, _), event) =>
                    taskLogger.warn(
                      s"A event reaches the end with STAGING status, ${event.summary}" +
                        exOp.map(ex => s", With error: $ex").getOrElse("")
                    )
                    event.getPassThrough[CommittableOffset] match {
                      case Some(co) =>
                        taskLogger.info(
                          s"A event is going to be committed to Kafka with STAGING status, ${event.summary}, With offset $co"
                        )
                        Success(co)
                      case None =>
                        val errorMessage =
                          s"A event missed PassThrough[CommittableOffset] with STAGING status, ${event.summary}"
                        taskLogger.error(errorMessage)
                        throw new IllegalStateException(errorMessage)
                    }

                  case (FAILED(ex, _), event) =>
                    taskLogger.error(s"A event reaches the end with error, ${event.summary}, Error: $ex")
                    // complete the stream if failure, before was using Future.successful(Done)
                    event.getPassThrough[CommittableOffset] match {
                      case Some(co) =>
                        taskLogger.info(
                          s"A event is going to be committed to Kafka with FAILED status, ${event.summary}, With offset $co"
                        )
                        throw new CommittableException(co, "FAILED status event")
                      case None =>
                        throw ex
                    }
                }
                .recover {
                  case ex: CommittableException =>
                    taskLogger.info(
                      s"The substream listening on topicPartition $topicPartition was failed with CommittableException, " +
                        s"Now recovering the last item to be a Success()"
                    )
                    Success(ex.getCommittableOffset())
                  case NonFatal(ex) =>
                    taskLogger.info(
                      s"The substream listening on topicPartition $topicPartition was failed with error: $ex, " +
                        s"Now recovering the last item to be a Failure()"
                    )
                    Failure(ex)
                }
                .collect {
                  case Success(co) =>
                    taskLogger.debug(s"Going to commit to Kafka with offset $co")
                    co
                }
                .batch(max = settings.commitMaxBatches, first => CommittableOffsetBatch.empty.updated(first)) {
                  (batch, elem) =>
                    batch.updated(elem)
                }
                .mapAsyncUnordered(1) { co =>
                  val reCommitFunc = {
                    case ex: RetriableCommitFailedException =>
                      taskLogger.warn(s"Commit offsets to kafka failed and recommitting now. offset: $co")
                      co.commitScaladsl().recoverWith(reCommitFunc)
                  }
                  co.commitScaladsl().recoverWith(reCommitFunc)
                }
                .toMat(Sink.ignore)(Keep.right)
                .run()
                .map(done => {
                  taskLogger
                    .info(
                      s"The substream listening on topicPartition $topicPartition was completed."
                    )
                  done
                })
              // comment this to let the stream fail when error happens
              /*.recover {
                  case NonFatal(ex) =>
                    taskLogger.error(
                      s"The substream listening on topicPartition $topicPartition was failed with error: $ex"
                    )
                    Done
                }*/
            } catch {
              case NonFatal(ex) ⇒
                taskLogger.error(
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
    taskLogger.info(s"Shutting down Kafka Source.")
    killSwitchOption.foreach(k => {
      k.shutdown(); killSwitchOption = None
    })
  }
}

object KafkaSource {

  class CommittableException(committableOffset: CommittableOffset, message: String) extends RuntimeException(message) {
    def getCommittableOffset(): CommittableOffset = committableOffset
  }

}

case class KafkaSourceSettings(
    bootstrapServers: String,
    groupId: Option[String],
    subscribedTopics: Either[Set[String], String],
    maxConcurrentPartitions: Int = 1024,
    commitMaxBatches: Int = 50,
    clientSettings: KafkaSourceClientSettings
)

case class KafkaSourceClientSettings(
    useDispatcher: Option[String] = None,
    pollInterval: Option[FiniteDuration] = None,
    pollTimeout: Option[FiniteDuration] = None,
    stopTimeout: Option[FiniteDuration] = None,
    closeTimeout: Option[FiniteDuration] = None,
    commitTimeout: Option[FiniteDuration] = None,
    commitTimeWarning: Option[FiniteDuration] = None,
    wakeupTimeout: Option[FiniteDuration] = None,
    maxWakeups: Option[Int] = None,
    waitClosePartition: Option[FiniteDuration] = None,
    wakeupDebug: Option[Boolean] = None,
    properties: Map[String, String] = Map.empty
)

class KafkaSourceBuilder() extends ITaskBuilder[KafkaSource] {

  override val taskType: String = "kafka"

  override val defaultConfig: Config =
    ConfigFactory.parseString(
      """{
        |  # bootstrap-servers = ""
        |  # group-id = ""
        |
        |  # Will use either "topics" or "topic-pattern"
        |  # topics = []
        |  # topic-pattern = event-* # supports wildcard if topics are defined will use that one
        |
        |  max-concurrent-partitions = 1024
        |  commit-max-batches = 50
        |
        |  akka-kafka {
        |    # poll-interval = 50ms
        |    # poll-timeout = 50ms
        |    # stop-timeout = 30s
        |    # close-timeout = 20s
        |    # commit-timeout = 15s
        |    # commit-time-warning = 1s
        |    # wakeup-timeout = 3s
        |    # max-wakeups = 10
        |    # use-dispatcher = "akka.kafka.default-dispatcher"
        |    # wait-close-partition = 500ms
        |    # wakeup-debug = true
        |  }
        |
        |  # Properties defined by org.apache.kafka.clients.consumer.ConsumerConfig
        |  # can be defined in this configuration section.
        |  properties {
        |    # Disable auto-commit by default
        |    "enable.auto.commit" = false
        |  }
        |}""".stripMargin
    )

  override def buildTask(
      config: Config
  )(implicit appContext: AppContext): KafkaSource = {
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

    val akkaKafkaConfig = config.getConfig("akka-kafka")
    val clientSettings = KafkaSourceClientSettings(
      akkaKafkaConfig.as[Option[String]]("use-dispatcher"),
      akkaKafkaConfig.as[Option[FiniteDuration]]("poll-interval"),
      akkaKafkaConfig.as[Option[FiniteDuration]]("poll-timeout"),
      akkaKafkaConfig.as[Option[FiniteDuration]]("stop-timeout"),
      akkaKafkaConfig.as[Option[FiniteDuration]]("close-timeout"),
      akkaKafkaConfig.as[Option[FiniteDuration]]("commit-timeout"),
      akkaKafkaConfig.as[Option[FiniteDuration]]("commit-time-warning"),
      akkaKafkaConfig.as[Option[FiniteDuration]]("wakeup-timeout"),
      akkaKafkaConfig.as[Option[Int]]("max-wakeups"),
      akkaKafkaConfig.as[Option[FiniteDuration]]("wait-close-partition"),
      akkaKafkaConfig.as[Option[Boolean]]("wakeup-debug"),
      config.as[Map[String, String]]("properties")
    )

    val settings =
      KafkaSourceSettings(
        config.as[String]("bootstrap-servers"),
        config.as[Option[String]]("group-id"),
        subscribedTopics,
        config.as[Int]("max-concurrent-partitions"),
        config.as[Int]("commit-max-batches"),
        clientSettings
      )

    new KafkaSource(settings)
  }

}
