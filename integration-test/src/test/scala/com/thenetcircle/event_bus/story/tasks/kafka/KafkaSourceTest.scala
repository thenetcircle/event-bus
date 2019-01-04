package com.thenetcircle.event_bus.story.tasks.kafka

import java.util.Properties

import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.IntegrationTestBase
import com.thenetcircle.event_bus.event.EventStatus.NORM
import com.thenetcircle.event_bus.event.{Event, EventStatus}
import com.thenetcircle.event_bus.story.tasks.kafka.extended.{EventSerializer, KafkaKey, KafkaKeySerializer}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.scalatest.BeforeAndAfter

import scala.collection.mutable.ArrayBuffer

class KafkaSourceTest extends IntegrationTestBase with BeforeAndAfter {

  behavior of "KafkaSource"

  val bootstrapServers = appContext.getSystemConfig().getString("app.test.kafka.bootstrap-servers")
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

    /*val testSink   = TestSubscriber.probe[(EventStatus, Event)]
    val testSource = TestPublisher.probe[(EventStatus, Event)](0)
    val testFlow =
      Flow.fromSinkAndSource(Sink.fromSubscriber(testSink), Source.fromPublisher(testSource))*/

    var receivedEvents = ArrayBuffer.empty[Event]

    val testFlow = Flow[(EventStatus, Event)].map {
      case (_, event) =>
        receivedEvents.synchronized(receivedEvents += event)
        (NORM, event)
    }
    kafkaSource.runWith(testFlow)

    // Three partitions will subscribe three times
    /*val s1 = testSink.expectSubscription()
    val s2 = testSink.expectSubscription()
    val s3 = testSink.expectSubscription()
    val s4 = testSource.expectSubscription()
    s1.request(10); s2.request(10); s3.request(10)*/

    for (i <- 1 to 10) {
      val testEvent = createTestEvent(
        body = s"""
                |{
                |  "id": "event-id-$i",
                |  "title": "message.send",
                |  "verb": "send",
                |  "actor": {"id": "aid$i", "objectType": "atype"}
                |}
      """.stripMargin
      )
      producer.send(new ProducerRecord[ProducerKey, ProducerValue](testTopic, KafkaKey(testEvent), testEvent))
    }
    producer.flush()

    Thread.sleep(1000)

    println(receivedEvents)

    kafkaSource.shutdown()

    Thread.sleep(1000)
    println("--------------------------------")

    kafkaSource.runWith(testFlow)

    println(receivedEvents)

    /*
    val (status, event) = testSink.expectNext(30.seconds)

    event.uuid shouldEqual "eventid"

    s4.sendNext(status -> event)*/
  }

}
