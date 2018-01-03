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

package com.thenetcircle.event_bus.connectors.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import com.thenetcircle.event_bus.base.AkkaStreamSpec
import com.thenetcircle.event_bus.createTestEvent
import com.thenetcircle.event_bus.event.Event

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class HttpSinkSpec extends AkkaStreamSpec {

  def run(httpSink: HttpSink): (TestPublisher.Probe[Event], TestSubscriber.Probe[Event]) =
    TestSource
      .probe[Event]
      .viaMat(httpSink.graph)(Keep.left)
      .toMat(TestSink.probe[Event])(Keep.both)
      .run()

  def createHttpSink(expectedResponse: Option[String] = None,
                     fallback: Sink[Event, _] = Sink.ignore,
                     maxRetryTimes: Int = 1,
                     defaultRequest: HttpRequest = HttpRequest(),
                     defaultResponse: Try[HttpResponse] = Success(HttpResponse()))(
      sender: Flow[(HttpRequest, Event), (Try[HttpResponse], Event), _] =
        Flow[(HttpRequest, Event)].map {
          case (_, event) =>
            (defaultResponse, event)
        }
  )(implicit system: ActorSystem, materializer: Materializer): HttpSink = {
    val httpSinkSettings = createHttpSinkSettings(maxRetryTimes = maxRetryTimes,
                                                  defaultRequest = defaultRequest,
                                                  expectedResponse = expectedResponse)
    new HttpSink(httpSinkSettings, sender, fallback)
  }

  def createHttpSinkSettings(
      host: String = "localhost",
      port: Int = 8888,
      maxRetryTimes: Int = 10,
      defaultRequest: HttpRequest = HttpRequest(),
      expectedResponse: Option[String] = None
  )(implicit system: ActorSystem): HttpSinkSettings =
    HttpSinkSettings(host, port, maxRetryTimes, defaultRequest, expectedResponse)

  behavior of "HttpSink"

  it must "delivery successfully to the target with proper HttpResponse" in {
    val fallback = TestSubscriber.probe[Event]()
    val httpSink =
      createHttpSink(fallback = Sink.fromSubscriber(fallback))()
    val (incoming, succeed) = run(httpSink)

    val testEvent = createTestEvent()

    fallback.request(1)
    succeed.request(1)
    incoming.expectRequest()
    incoming.sendNext(testEvent)
    succeed.expectNext(testEvent)
    fallback.expectNoMsg(100.millisecond)
  }

  it should "retry multiple times before goes to failed/fallback" in {
    var senderTimes = 0
    val sender = Flow[(HttpRequest, Event)].map {
      case (_, event) =>
        senderTimes += 1
        (Failure(new Exception("failed response")), event)
    }
    val fallback = TestSubscriber.probe[Event]()
    val httpSink =
      createHttpSink(fallback = Sink.fromSubscriber(fallback), maxRetryTimes = 10)(sender)
    val (incoming, succeed) = run(httpSink)

    val testEvent = createTestEvent()

    fallback.request(1)
    succeed.request(1)
    incoming.sendNext(testEvent)
    succeed.expectNoMsg(100.millisecond)
    fallback.expectNext(testEvent)
    fallback.expectNoMsg(100.millisecond)
    senderTimes shouldEqual 10
  }

  it should "support async sender" in {
    var senderTimes = 0
    val sender = Flow[(HttpRequest, Event)].map {
      case (_, event) =>
        senderTimes += 1
        (Failure(new Exception("failed response")), event)
    }.async
    // .withAttributes(Attributes.inputBuffer(initial = 1, max = 1))

    val source = Source.fromIterator(
      () =>
        (for (i <- 1 to 10)
          yield createTestEvent(name = s"TestEvent$i")).iterator
    )

    val fallback = TestSubscriber.probe[Event]()
    val httpSink =
      createHttpSink(fallback = Sink.fromSubscriber(fallback), maxRetryTimes = 1)(sender)

    val succeed = source
      .via(httpSink.graph)
      .toMat(TestSink.probe[Event])(Keep.right)
      .run()

    succeed.request(1)
    for (i <- 1 to 10) {
      fallback.request(1)
      val _event = fallback.expectNext()

      _event.metadata.name shouldEqual s"TestEvent$i"

      if (i < 10) succeed.expectNoMsg(100.millisecond)
    }

    fallback.expectComplete()
    succeed.expectComplete()

    /*incoming.sendNext(testEvent)
        succeed.expectNoMsg(100.millisecond)
        fallback.expectNext(testEvent)
        fallback.expectNoMsg(100.millisecond)
        senderTimes shouldEqual 10*/
  }

  it should "properly support multiple ports" in {
    var senderTimes = 0
    val sender = Flow[(HttpRequest, Event)].map {
      case (_, event) =>
        senderTimes += 1
        (Success(HttpResponse(entity = HttpEntity(event.body.data))), event)
    }

    val fallback = TestSubscriber.probe[Event]()

    val httpSink =
      createHttpSink(fallback = Sink.fromSubscriber(fallback), expectedResponse = Some("OK"))(
        sender
      )

    val (in1, out1) = run(httpSink)
    fallback.expectSubscription().request(1)

    val (in2, out2) = run(httpSink)
    fallback.expectSubscription().request(1)

    val (in3, out3) = run(httpSink)
    fallback.expectSubscription().request(1)

    val okEvent = createTestEvent(body = "OK")
    val koEvent = createTestEvent(body = "KO")

    out1.request(1)
    out2.request(1)
    out3.request(1)

    in1.sendNext(okEvent)
    out1.expectNext(okEvent)
    out2.expectNoMsg(100.millisecond)
    out3.expectNoMsg(100.millisecond)

    in2.sendNext(okEvent)
    out2.expectNext(okEvent)
    out1.expectNoMsg(100.millisecond)
    out3.expectNoMsg(100.millisecond)

    in3.sendNext(okEvent)
    out3.expectNext(okEvent)
    out1.expectNoMsg(100.millisecond)
    out2.expectNoMsg(100.millisecond)

    fallback.expectNoMsg(100.millisecond)
    senderTimes shouldEqual 3

    out1.request(1)
    out2.request(1)
    out3.request(1)
    in3.sendNext(koEvent)
    out1.expectNoMsg(100.millisecond)
    out2.expectNoMsg(100.millisecond)
    out3.expectNoMsg(100.millisecond)

    fallback.expectNext(koEvent)
  }

  it should "goes to the target if succeed after several tries" in {
    var senderTimes = 0
    val sender = Flow[(HttpRequest, Event)].map {
      case (_, event) =>
        senderTimes += 1
        val response = if (senderTimes > 5) {
          Success(HttpResponse(entity = HttpEntity(event.body.data)))
        } else {
          Failure(new Exception("Let it retry"))
        }
        (response, event)
    }
    val fallback = TestSubscriber.probe[Event]()
    val httpSink =
      createHttpSink(fallback = Sink.fromSubscriber(fallback),
                     maxRetryTimes = 10,
                     expectedResponse = Some("OK"))(sender)

    val (testSource, testSink) = run(httpSink)
    val testEvent              = createTestEvent(body = "OK")

    testSink.request(1)
    fallback.request(1)
    testSource.sendNext(testEvent)
    testSink.expectNext(testEvent)
    fallback.expectNoMsg(100.microsecond)
    senderTimes shouldEqual 6

    testSink.request(1)
    testSource.sendNext(testEvent)
    testSink.expectNext(testEvent)
    fallback.expectNoMsg(100.microsecond)
    senderTimes shouldEqual 7
  }

  it must "support mixed requests" in {
    var senderTimes = 0
    val sender = Flow[(HttpRequest, Event)].map {
      case (_, event) =>
        senderTimes += 1
        (Success(HttpResponse(entity = HttpEntity(event.body.data))), event)
    }
    val fallback = TestSubscriber.probe[Event]()
    val httpSink =
      createHttpSink(fallback = Sink.fromSubscriber(fallback),
                     maxRetryTimes = 10,
                     expectedResponse = Some("OK"))(sender)

    val okEvent = createTestEvent(name = "okEvent", body = "OK")
    val koEvent = createTestEvent(name = "koEvent", body = "KO")

    val source1 = Source.fromIterator(() => Seq.fill(100)(okEvent).iterator)
    val source2 = Source.fromIterator(() => Seq.fill(10)(koEvent).iterator)
    val source3 = Source.fromIterator(
      () => (for (_ <- 1 to 100) yield koEvent :: okEvent :: Nil).flatten.iterator
    )

    val sink1 =
      source1
        .via(httpSink.graph)
        .toMat(TestSink.probe[Event])(Keep.right)
        .run()
    val fallbackSub1 = fallback.expectSubscription()

    val sink2 =
      source2
        .via(httpSink.graph)
        .toMat(TestSink.probe[Event])(Keep.right)
        .run()
    val fallbackSub2 = fallback.expectSubscription()

    val sink3 =
      source3
        .via(httpSink.graph)
        .toMat(TestSink.probe[Event])(Keep.right)
        .run()
    val fallbackSub3 = fallback.expectSubscription()

    fallbackSub1.request(1)
    for (i <- 1 to 100) {
      sink1.request(1)
      sink1.expectNext(okEvent)
    }
    fallback.expectComplete()
    sink1.expectComplete()

    sink2.request(1)
    for (i <- 1 to 10) {
      fallbackSub2.request(1)
      fallback.expectNext(koEvent)
    }
    fallback.expectComplete()

    fallbackSub3.request(1)
    for (i <- 1 to 100) {
      sink3.request(1)
      fallback.expectNext(koEvent)

      fallbackSub3.request(1)
      sink3.expectNext(okEvent)
    }
    sink3.expectComplete()
    fallback.expectComplete()
  }

  // TODO: test abnormal cases, like exception, cancel, error, complete etc...

}
