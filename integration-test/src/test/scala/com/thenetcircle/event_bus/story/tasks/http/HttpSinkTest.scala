package com.thenetcircle.event_bus.story.tasks.http

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, Uri}
import akka.pattern.AskTimeoutException
import akka.stream.scaladsl.{Flow, Keep}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import com.thenetcircle.event_bus.IntegrationTestBase
import com.thenetcircle.event_bus.event.EventStatus.{FAIL, NORM, TOFB}
import com.thenetcircle.event_bus.event.{Event, EventStatus}

import scala.concurrent.Await
import scala.concurrent.duration._

class HttpSinkTest extends IntegrationTestBase {

  behavior of "HttpSink"

  def sendToUri(uri: String): (TestPublisher.Probe[Event], TestSubscriber.Probe[(EventStatus, Event)]) = {
    val settings = HttpSinkSettings(
      defaultRequest = HttpRequest(uri = Uri(uri)),
      maxRetryTime = 5.seconds
    )

    val story = new HttpSink(settings)

    val flow: Flow[Event, (EventStatus, Event), NotUsed] = story.flow()

    TestSource
      .probe[Event]
      .via(flow)
      .toMat(TestSink.probe)(Keep.both)
      .run()
  }

  it should "get a FAIL event when send to unreachable endpoint" in {

    val (source, sink) = sendToUri("http://www.unreachableendpoint.com")

    val testEvent = createTestEvent()

    source.sendNext(testEvent)

    sink.request(1)
    val (status, event) = sink.expectNext(10.seconds)

    status shouldBe a[FAIL]
    status.asInstanceOf[FAIL].cause shouldBe a[AskTimeoutException]
    event shouldEqual testEvent

  }

  it should "get a TOFB event when send to reachable endpoint with unexpected response" in {

    val (source, sink) = sendToUri("http://www.baidu.com")

    val testEvent = createTestEvent()

    source.sendNext(testEvent)

    sink.request(1)
    val (status, event) = sink.expectNext(10.seconds)

    status shouldBe a[TOFB]
    status.asInstanceOf[TOFB].cause.get shouldBe a[UnexpectedResponseException]
    event shouldEqual testEvent

  }

  it should "get a NORM event when send to reachable endpoint with expected response" in {

    val tempBind = Http().bindAndHandle(Flow[HttpRequest].map(_ => {
      HttpResponse(entity = HttpEntity("ok"))
    }), interface = "127.0.0.1", port = 55600)

    val (source, sink) = sendToUri("http://127.0.0.1:55600")

    val testEvent = createTestEvent()

    source.sendNext(testEvent)

    sink.request(1)
    val (status, event) = sink.expectNext(10.seconds)

    status shouldEqual NORM
    event shouldEqual testEvent

    Await.ready(tempBind.flatMap(_.unbind()), 3.seconds)

  }

}