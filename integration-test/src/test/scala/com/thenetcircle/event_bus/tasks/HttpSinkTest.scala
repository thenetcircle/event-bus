package com.thenetcircle.event_bus.tasks

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.pattern.AskTimeoutException
import akka.stream.scaladsl.{Flow, Keep}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import com.thenetcircle.event_bus.IntegrationTestBase
import com.thenetcircle.event_bus.interfaces.EventStatus.Fail
import com.thenetcircle.event_bus.interfaces.{Event, EventStatus}
import com.thenetcircle.event_bus.tasks.http.{HttpSink, HttpSinkSettings}

import scala.concurrent.duration._

class HttpSinkTest extends IntegrationTestBase {

  behavior of "HttpSink"

  it should "get a Fail event when send to unreachable endpoint" in {

    val settings = HttpSinkSettings(
      defaultRequest = HttpRequest(uri = Uri("http://www.unreachableendpoint.com")),
      expectedResponseBody = "ok",
      maxRetryTime = 5.seconds
    )

    val story = new HttpSink(settings)

    val flow: Flow[Event, (EventStatus, Event), NotUsed] = story.prepare()
    val (source, sink) =
      TestSource
        .probe[Event]
        .via(flow)
        .toMat(TestSink.probe)(Keep.both)
        .run()

    val testEvent = createTestEvent()

    source.sendNext(testEvent)

    sink.request(1)
    val (status, event) = sink.expectNext(10.seconds)

    status shouldBe a[Fail]
    status.asInstanceOf[Fail].cause shouldBe a[AskTimeoutException]
    event shouldEqual testEvent

  }

}
