package com.thenetcircle.event_bus.tasks

import java.util.Properties

import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import com.thenetcircle.event_bus.IntegrationTestBase
import com.thenetcircle.event_bus.interfaces.{Event, EventStatus}
import com.thenetcircle.event_bus.tasks.kafka.extended.{EventSerializer, KafkaKey, KafkaKeySerializer}
import com.thenetcircle.event_bus.tasks.kafka.{KafkaSource, KafkaSourceSettings, ProducerKey, ProducerValue}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.scalatest.BeforeAndAfter

import scala.concurrent.duration._

class KafkaSourceTest extends IntegrationTestBase with BeforeAndAfter {

  behavior of "KafkaSource"

  val bootstrapServers = appContext.getSystemConfig().getString("app.kafka-bootstrap-servers")
  val testTopic        = "event-bus-integration-test"

  val settings = KafkaSourceSettings(
    bootstrapServers = bootstrapServers,
    groupId = Some("event-bus-integration-test-group-" + System.currentTimeMillis()),
    subscribedTopics = Left(Set[String](testTopic))
    /*properties = Map(
      "" -> ""
    )*/
  )
  val kafkaSource = new KafkaSource(settings)

  val props = new Properties()
  props.put("bootstrap.servers", bootstrapServers)
  props.put("key.serializer", classOf[KafkaKeySerializer])
  props.put("value.serializer", classOf[EventSerializer])
  val producer = new KafkaProducer[ProducerKey, ProducerValue](props)

  after {
    producer.close()
    kafkaSource.shutdown()
  }

  it should "responds error when get non json request" in {
    val testEvent = createTestEvent(
      body = s"""
        |{
        |  "id": "eventid",
        |  "title": "message.send",
        |  "verb": "send",
        |  "actor": {"id": "aid", "objectType": "atype"}
        |}
      """.stripMargin
    )

    val testSink   = TestSubscriber.probe[(EventStatus, Event)]
    val testSource = TestPublisher.probe[(EventStatus, Event)](0)
    val testFlow =
      Flow.fromSinkAndSource(Sink.fromSubscriber(testSink), Source.fromPublisher(testSource))

    kafkaSource.runWith(testFlow)

    // Three partitions will subscribe three times
    val s1 = testSink.expectSubscription()
    val s2 = testSink.expectSubscription()
    val s3 = testSink.expectSubscription()
    val s4 = testSource.expectSubscription()
    s1.request(10); s2.request(10); s3.request(10)

    producer.send(new ProducerRecord[ProducerKey, ProducerValue](testTopic, KafkaKey(testEvent), testEvent))
    producer.flush()

    Thread.sleep(100)

    val (status, event) = testSink.expectNext(30.seconds)

    event.uuid shouldEqual "eventid"

    s4.sendNext(status -> event)
  }

}
