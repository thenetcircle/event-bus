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

package com.thenetcircle.event_bus.tasks.http
/*
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import com.thenetcircle.event_bus.base.BaseTest
import com.thenetcircle.event_bus.createTestEvent
import com.thenetcircle.event_bus.event.{Event, EventStatus}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class HttpSinkTest extends BaseTest {

  def run(httpSink: HttpSink): (TestPublisher.Probe[Event], TestSubscriber.Probe[Event]) =
    TestSource
      .probe[Event]
      .viaMat(httpSink.getGraph())(Keep.left)
      .toMat(TestSink.probe[Event])(Keep.both)
      .run()

  def createHttpSink(expectedResponse: Option[String] = None,
                     maxRetryTimes: Int = 1,
                     defaultRequest: HttpRequest = HttpRequest(),
                     defaultResponse: Try[HttpResponse] = Success(HttpResponse()))(
      sender: Flow[(HttpRequest, Event), (Try[HttpResponse], Event), _] =
        Flow[(HttpRequest, Event)].map {
          case (_, event) =>
            (defaultResponse, event)
        }
  )(implicit system: ActorSystem, materializer: Materializer): HttpSink = {
    val httpSinkSettings = createHttpSinkSettings(
      maxRetryTimes = maxRetryTimes,
      defaultRequest = defaultRequest,
      expectedResponse = expectedResponse
    )
    new HttpSink(httpSinkSettings, Some(sender))
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
    val httpSink = createHttpSink()()
    val (incoming, result) = run(httpSink)

    val testEvent = createTestEvent()

    result.request(1)
    incoming.expectRequest()
    incoming.sendNext(testEvent)
    result.expectNext(testEvent)
  }

  it should "withRetry multiple times before goes to failed/fallback" in {
    var senderTimes = 0
    val sender = Flow[(HttpRequest, Event)].map {
      case (_, event) =>
        senderTimes += 1
        (Failure(new Exception("failed response")), event)
    }
    val httpSink = createHttpSink(maxRetryTimes = 10)(sender)
    val (incoming, result) = run(httpSink)

    val testEvent = createTestEvent()

    result.request(1)
    incoming.sendNext(testEvent)

    val _event = result.expectNext()
    _event shouldEqual testEvent.withStatus(EventStatus.FAILED)

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

    val httpSink = createHttpSink(maxRetryTimes = 1)(sender)

    val result = source
      .via(httpSink.getGraph())
      .toMat(TestSink.probe[Event])(Keep.right)
      .run()

    for (i <- 1 to 10) {
      val _event = result.requestNext()

      _event.metadata.name shouldEqual s"TestEvent$i"
      _event.isFailed shouldEqual true
    }

    result.expectComplete()
  }

  it should "properly support multiple ports" in {
    var senderTimes = 0
    val sender = Flow[(HttpRequest, Event)].map {
      case (_, event) =>
        senderTimes += 1
        (Success(HttpResponse(entity = HttpEntity(event.body.data))), event)
    }

    val httpSink = createHttpSink(expectedResponse = Some("OK"))(sender)

    val (in1, out1) = run(httpSink)
    val (in2, out2) = run(httpSink)
    val (in3, out3) = run(httpSink)

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

    senderTimes shouldEqual 3

    out1.request(1)
    out2.request(1)
    out3.request(1)
    in3.sendNext(koEvent)
    out1.expectNoMsg(100.millisecond)
    out2.expectNoMsg(100.millisecond)

    out3.expectNext() shouldEqual koEvent.withStatus(EventStatus.FAILED)
  }

  it should "goes to the target if succeed after several tries" in {
    var senderTimes = 0
    val sender = Flow[(HttpRequest, Event)].map {
      case (_, event) =>
        senderTimes += 1
        val response = if (senderTimes > 5) {
          Success(HttpResponse(entity = HttpEntity(event.body.data)))
        } else {
          Failure(new Exception("Let it withRetry"))
        }
        (response, event)
    }

    val httpSink =
      createHttpSink(maxRetryTimes = 10, expectedResponse = Some("OK"))(sender)

    val (testSource, testSink) = run(httpSink)
    val testEvent = createTestEvent(body = "OK")

    testSink.request(1)
    testSource.sendNext(testEvent)
    testSink.expectNext(testEvent)
    senderTimes shouldEqual 6

    testSink.request(1)
    testSource.sendNext(testEvent)
    testSink.expectNext(testEvent)
    senderTimes shouldEqual 7
  }

  it must "support mixed requests" in {
    var senderTimes = 0
    val sender = Flow[(HttpRequest, Event)].map {
      case (_, event) =>
        senderTimes += 1
        (Success(HttpResponse(entity = HttpEntity(event.body.data))), event)
    }

    val httpSink =
      createHttpSink(maxRetryTimes = 10, expectedResponse = Some("OK"))(sender)

    val okEvent = createTestEvent(name = "okEvent", body = "OK")
    val koEvent = createTestEvent(name = "koEvent", body = "KO")

    val source1 = Source.fromIterator(() => Seq.fill(100)(okEvent).iterator)
    val source2 = Source.fromIterator(() => Seq.fill(10)(koEvent).iterator)
    val source3 = Source.fromIterator(
      () => (for (_ <- 1 to 100) yield koEvent :: okEvent :: Nil).flatten.iterator
    )

    val sink1 =
      source1
        .via(httpSink.getGraph())
        .toMat(TestSink.probe[Event])(Keep.right)
        .run()

    val sink2 =
      source2
        .via(httpSink.getGraph())
        .toMat(TestSink.probe[Event])(Keep.right)
        .run()

    val sink3 =
      source3
        .via(httpSink.getGraph())
        .toMat(TestSink.probe[Event])(Keep.right)
        .run()

    for (i <- 1 to 100) {
      sink1.request(1)
      sink1.expectNext(okEvent)
    }
    sink1.expectComplete()

    for (i <- 1 to 10) {
      sink2.request(1)
      sink2.expectNext(koEvent.withStatus(EventStatus.FAILED))
    }

    for (i <- 1 to 100) {
      sink3.request(2)
      sink3.expectNext(koEvent.withStatus(EventStatus.FAILED), okEvent)
    }
    sink3.expectComplete()
  }

  // TODO: test abnormal cases, like exception, cancel, error, complete etc...

}
 */
