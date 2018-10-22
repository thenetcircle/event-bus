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

import java.util
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import akka.NotUsed
import akka.kafka.ProducerMessage.{Envelope, Message, Result}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Sink}
import akka.stream.stage._
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.interfaces.EventStatus.Norm
import com.thenetcircle.event_bus.interfaces.{Event, EventStatus, SinkTask, SinkTaskBuilder}
import com.thenetcircle.event_bus.misc.{MissedEventHandler, Util}
import com.thenetcircle.event_bus.tasks.kafka.extended.{EventSerializer, KafkaKey, KafkaKeySerializer, KafkaPartitioner}
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}

import scala.concurrent.duration._

case class KafkaSinkSettings(
    bootstrapServers: String,
    defaultTopic: String = "event-default",
    parallelism: Int = 100,
    closeTimeout: FiniteDuration = 60.seconds,
    useDispatcher: Option[String] = None,
    properties: Map[String, String] = Map.empty,
    useAsyncBuffer: Boolean = true,
    asyncBufferSize: Int = 100
)

class KafkaSink(val settings: KafkaSinkSettings) extends SinkTask with StrictLogging {

  require(settings.bootstrapServers.nonEmpty, "bootstrap servers is required.")

  logger.info(s"Initializing KafkaSink with settings: $settings")

  def getProducerSettings()(
      implicit runningContext: TaskRunningContext
  ): ProducerSettings[ProducerKey, ProducerValue] = {
    var _producerSettings = ProducerSettings[ProducerKey, ProducerValue](
      runningContext.getActorSystem(),
      new KafkaKeySerializer,
      new EventSerializer
    )

    settings.properties.foreach {
      case (_key, _value) => _producerSettings = _producerSettings.withProperty(_key, _value)
    }

    settings.useDispatcher.foreach(dp => _producerSettings = _producerSettings.withDispatcher(dp))

    val clientId = s"eventbus-${runningContext.getAppContext().getAppName()}"

    _producerSettings
      .withParallelism(settings.parallelism)
      .withCloseTimeout(settings.closeTimeout)
      .withProperty(ProducerConfig.PARTITIONER_CLASS_CONFIG, classOf[KafkaPartitioner].getName)
      .withBootstrapServers(settings.bootstrapServers)
    // .withProperty("client.id", clientId)
  }

  def createEnvelope(event: Event)(
      implicit runningContext: TaskRunningContext
  ): Envelope[ProducerKey, ProducerValue, Event] = {
    val record = createProducerRecord(event)
    logger.debug(s"new kafka record $record is created")
    Message(record, event)
  }

  def createProducerRecord(event: Event)(
      implicit runningContext: TaskRunningContext
  ): ProducerRecord[ProducerKey, ProducerValue] = {
    var topic: String        = event.metadata.topic.getOrElse(settings.defaultTopic)
    val key: ProducerKey     = KafkaKey(event)
    val value: ProducerValue = event
    // val timestamp: Long      = event.createdAt.getTime

    /*new ProducerRecord[ProducerKey, ProducerValue](
      topic,
      null,
      timestamp.asInstanceOf[java.lang.Long],
      key,
      value
    )*/

    new ProducerRecord[ProducerKey, ProducerValue](
      topic,
      key,
      value
    )
  }

  var kafkaProducer: Option[KafkaProducer[ProducerKey, ProducerValue]] = None

  override def prepare()(
      implicit runningContext: TaskRunningContext
  ): Flow[Event, (EventStatus, Event), NotUsed] = {

    val kafkaSettings = getProducerSettings()

    val _kafkaProducer = kafkaProducer.getOrElse({
      logger.info("creating new kakfa producer")
      kafkaProducer = Some(kafkaSettings.createKafkaProducer())
      kafkaProducer.get
    })

    // Note that the flow might be materialized multiple times,
    // like from HttpSource(multiple connections), KafkaSource(multiple topicPartitions)
    // DONE issue when send to new topics, check here https://github.com/akka/reactive-kafka/issues/163
    // DONE protects that the stream crashed by sending failure
    // DONE use Producer.flexiFlow
    // DONE optimize logging
    val producingFlow = Flow[Event]
      .map(createEnvelope)
      .via(Producer.flexiFlow(kafkaSettings, _kafkaProducer))
      .map {
        case Result(metadata, message) =>
          val eventBrief = Util.getBriefOfEvent(message.passThrough)
          val kafkaBrief =
            s"topic: ${metadata.topic()}, partition: ${metadata.partition()}, offset: ${metadata
              .offset()}, key: ${Option(message.record.key()).map(_.rawData).getOrElse("")}"
          logger.info(s"sending event [$eventBrief] to kafka [$kafkaBrief] succeeded.")

          (Norm, message.passThrough)
      }

    if (settings.useAsyncBuffer) {
      logger.debug("wrapping async buffer")
      KafkaSink.wrapAsyncBuffer(settings.asyncBufferSize, producingFlow)
    } else {
      producingFlow
    }
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    logger.info(s"shutting down kafka-sink of story ${runningContext.getStoryName()}.")
    kafkaProducer.foreach(k => {
      k.close(5, TimeUnit.SECONDS); kafkaProducer = None
    })
  }
}

object KafkaSink {

  def wrapAsyncBuffer(bufferSize: Int, producingFlow: Flow[Event, _, _]): Flow[Event, (EventStatus, Event), NotUsed] = {
    val producingSink: Sink[Event, _] = producingFlow
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      .to(Sink.ignore)

    Flow
      .fromGraph(
        GraphDSL
          .create() { implicit builder =>
            import GraphDSL.Implicits._
            val buffer = builder.add(new AsyncBuffer(bufferSize))
            buffer.out1 ~> producingSink
            FlowShape(buffer.in, buffer.out0)
          }
      )
  }

  class AsyncBuffer(bufferSize: Int) extends GraphStage[FanOutShape2[Event, (EventStatus, Event), Event]] {

    val in   = Inlet[Event]("AsyncBuffer.in")
    val out0 = Outlet[(EventStatus, Event)]("AsyncBuffer.out0")
    val out1 = Outlet[Event]("AsyncBuffer.out1")

    val shape: FanOutShape2[Event, (EventStatus, Event), Event] = new FanOutShape2(in, out0, out1)

    override def createLogic(
        inheritedAttributes: Attributes
    ): GraphStageLogic = new GraphStageLogic(shape) with InHandler with StageLogging {
      private val buffer: util.Queue[Event] = new LinkedBlockingQueue(bufferSize)

      private def flushBuffer(): Unit =
        while (!buffer.isEmpty) {
          MissedEventHandler.handle(buffer.poll())
        }

      override def onPush(): Unit = {
        val event = grab(in)

        if (isAvailable(out0)) {
          push(out0, (Norm, event))
        }

        if (buffer.isEmpty && isAvailable(out1)) {
          push(out1, event)
        } else {
          if (!buffer.offer(event)) { // if the buffer is full
            log.warning("A event [" + Util.getBriefOfEvent(event) + "] is dropped since the AsyncBuffer is full.")
            MissedEventHandler.handle(event)
          }
        }
      }

      override def onUpstreamFinish(): Unit =
        if (buffer.isEmpty) completeStage()

      override def postStop(): Unit = flushBuffer()

      setHandler(in, this)

      // outlet for outside
      setHandler(
        out0,
        new OutHandler {
          override def onPull(): Unit =
            if (isClosed(in)) {
              if (buffer.isEmpty) completeStage()
            } else if (!hasBeenPulled(in)) {
              pull(in)
            }

          override def onDownstreamFinish(): Unit =
            if (buffer.isEmpty) completeStage()
        }
      )

      // outlet for kafka producer
      setHandler(
        out1,
        new OutHandler {
          override def onPull(): Unit = {
            if (!buffer.isEmpty) push(out1, buffer.poll())
            if (isClosed(in) && buffer.isEmpty) completeStage()
          }

          override def onDownstreamFinish(): Unit = {
            flushBuffer()
            super.onDownstreamFinish()
          }
        }
      )
    }

  }

}

class KafkaSinkBuilder() extends SinkTaskBuilder {
  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): KafkaSink = {
    val config = Util
      .convertJsonStringToConfig(configString)
      .withFallback(buildingContext.getSystemConfig().getConfig("task.kafka-sink"))

    val settings =
      KafkaSinkSettings(
        config.as[String]("bootstrap-servers"),
        config.as[String]("default-topic"),
        config.as[Int]("parallelism"),
        config.as[FiniteDuration]("close-timeout"),
        config.as[Option[String]]("use-dispatcher"),
        config.as[Map[String, String]]("properties"),
        config.as[Boolean]("use-async-buffer"),
        config.as[Int]("async-buffer-size")
      )

    new KafkaSink(settings)
  }
}
