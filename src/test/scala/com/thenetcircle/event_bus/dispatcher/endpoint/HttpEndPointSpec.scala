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

package com.thenetcircle.event_bus.dispatcher.endpoint

import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.testkit.AkkaBaseSpec
import com.thenetcircle.event_bus.testkit.TestComponentBuilder._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class HttpEndPointSpec extends AkkaBaseSpec {

  behavior of "HttpEndPoint"

  it must "delivery successfully to the target with proper HttpResponse" in {
    val fallbacker = TestSubscriber.probe[Event]()
    val endPoint =
      createHttpEndPoint(fallbacker = Sink.fromSubscriber(fallbacker))()
    val (incoming, succeed) = run(endPoint)

    val testEvent = createTestEvent()

    fallbacker.request(1)
    succeed.request(1)
    incoming.expectRequest()
    incoming.sendNext(testEvent)
    succeed.expectNext(testEvent)
    fallbacker.expectNoMsg(100.millisecond)
  }

  it should "retry multiple times before goes to failed/fallbacker" in {
    var senderTimes = 0
    val sender = Flow[(HttpRequest, Event)].map {
      case (_, event) =>
        senderTimes += 1
        (Failure(new Exception("failed response")), event)
    }
    val fallbacker = TestSubscriber.probe[Event]()
    val endPoint = createHttpEndPoint(
      fallbacker = Sink.fromSubscriber(fallbacker),
      maxRetryTimes = 10
    )(sender)
    val (incoming, succeed) = run(endPoint)

    val testEvent = createTestEvent()

    fallbacker.request(1)
    succeed.request(1)
    incoming.sendNext(testEvent)
    succeed.expectNoMsg(100.millisecond)
    fallbacker.expectNext(testEvent)
    fallbacker.expectNoMsg(100.millisecond)
    senderTimes shouldEqual 10
  }

  it should "properly support multiple ports" in {
    var senderTimes = 0
    val sender = Flow[(HttpRequest, Event)].map {
      case (_, event) =>
        senderTimes += 1
        (Success(HttpResponse(entity = HttpEntity(event.body.data))), event)
    }

    val fallbacker = TestSubscriber.probe[Event]()

    val endPoint =
      createHttpEndPoint(
        fallbacker = Sink.fromSubscriber(fallbacker),
        expectedResponse = Some("OK")
      )(sender)

    val (in1, out1) = run(endPoint)
    fallbacker.expectSubscription().request(1)

    val (in2, out2) = run(endPoint)
    fallbacker.expectSubscription().request(1)

    val (in3, out3) = run(endPoint)
    fallbacker.expectSubscription().request(1)

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

    fallbacker.expectNoMsg(100.millisecond)
    senderTimes shouldEqual 3

    out1.request(1)
    out2.request(1)
    out3.request(1)
    in3.sendNext(koEvent)
    out1.expectNoMsg(100.millisecond)
    out2.expectNoMsg(100.millisecond)
    out3.expectNoMsg(100.millisecond)

    fallbacker.expectNext(koEvent)
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
    val fallbacker = TestSubscriber.probe[Event]()
    val endPoint =
      createHttpEndPoint(
        fallbacker = Sink.fromSubscriber(fallbacker),
        maxRetryTimes = 10,
        expectedResponse = Some("OK")
      )(sender)

    val (testSource, testSink) = run(endPoint)
    val testEvent              = createTestEvent(body = "OK")

    testSink.request(1)
    fallbacker.request(1)
    testSource.sendNext(testEvent)
    testSink.expectNext(testEvent)
    fallbacker.expectNoMsg(100.microsecond)
    senderTimes shouldEqual 6

    testSink.request(1)
    testSource.sendNext(testEvent)
    testSink.expectNext(testEvent)
    fallbacker.expectNoMsg(100.microsecond)
    senderTimes shouldEqual 7
  }

  it must "support mixed requests" in {
    var senderTimes = 0
    val sender = Flow[(HttpRequest, Event)].map {
      case (_, event) =>
        senderTimes += 1
        (Success(HttpResponse(entity = HttpEntity(event.body.data))), event)
    }
    val fallbacker = TestSubscriber.probe[Event]()
    val endPoint =
      createHttpEndPoint(
        fallbacker = Sink.fromSubscriber(fallbacker),
        maxRetryTimes = 10,
        expectedResponse = Some("OK")
      )(sender)

    val okEvent = createTestEvent(name = "okEvent", body = "OK")
    val koEvent = createTestEvent(name = "koEvent", body = "KO")

    val source1 = Source.fromIterator(() => Seq.fill(100)(okEvent).iterator)
    val source2 = Source.fromIterator(() => Seq.fill(10)(koEvent).iterator)
    val source3 = Source.fromIterator(() =>
      (for (_ <- 1 to 100) yield koEvent :: okEvent :: Nil).flatten.iterator)

    val sink1 =
      source1.via(endPoint.port).toMat(TestSink.probe[Event])(Keep.right).run()
    val fallbackerSub1 = fallbacker.expectSubscription()

    val sink2 =
      source2.via(endPoint.port).toMat(TestSink.probe[Event])(Keep.right).run()
    val fallbackerSub2 = fallbacker.expectSubscription()

    val sink3 =
      source3.via(endPoint.port).toMat(TestSink.probe[Event])(Keep.right).run()
    val fallbackerSub3 = fallbacker.expectSubscription()

    fallbackerSub1.request(1)
    for (i <- 1 to 100) {
      sink1.request(1)
      sink1.expectNext(okEvent)
    }
    fallbacker.expectComplete()
    sink1.expectComplete()

    sink2.request(1)
    for (i <- 1 to 10) {
      fallbackerSub2.request(1)
      fallbacker.expectNext(koEvent)
    }
    fallbacker.expectComplete()

    fallbackerSub3.request(1)
    for (i <- 1 to 100) {
      sink3.request(1)
      fallbacker.expectNext(koEvent)

      fallbackerSub3.request(1)
      sink3.expectNext(okEvent)
    }
    sink3.expectComplete()
    fallbacker.expectComplete()
  }

  // TODO: test abnormal cases, like exception, cancel, error, complete etc...

  def run(endPoint: HttpEndPoint)
    : (TestPublisher.Probe[Event], TestSubscriber.Probe[Event]) =
    TestSource
      .probe[Event]
      .viaMat(endPoint.port)(Keep.left)
      .toMat(TestSink.probe[Event])(Keep.both)
      .run()
}
