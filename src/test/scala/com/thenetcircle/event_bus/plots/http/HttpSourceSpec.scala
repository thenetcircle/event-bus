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

package com.thenetcircle.event_bus.plots.http

import java.text.SimpleDateFormat

import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.util.ByteString
import com.thenetcircle.event_bus.base.AkkaStreamSpec
import com.thenetcircle.event_bus.plots.http.HttpSource.successfulResponse
import com.thenetcircle.event_bus.event.extractor.DataFormat
import com.thenetcircle.event_bus.event.{Event, EventBody, EventMetaData}

import scala.concurrent.Promise
import scala.concurrent.duration._

class HttpSourceSpec extends AkkaStreamSpec {

  behavior of "HttpSource"

  it should "return a BadRequest of HttpResponse when empty request coming" in {
    val (in, out0, out1) = getTestPorts

    out1.request(1)
    out0.request(1)

    in.sendNext(HttpRequest())

    out1.expectNoMsg(100.millisecond)

    val res = out0.expectNext(100.millisecond)
    res.status shouldEqual StatusCodes.BadRequest
  }

  it should "return a BadRequest of HttpResponse when a unaccepted request body coming" in {
    val (in, out0, out1) = getTestPorts

    out1.request(1)
    out0.request(1)
    // sub2.request(1)
    in.sendNext(HttpRequest(entity = HttpEntity(s"""
                                                       |{
                                                       |  "title": "user.login"
                                                       |}
                                                    """)))

    val res = out0.expectNext()
    res.status shouldEqual StatusCodes.BadRequest
    out1.expectNoMsg(100.millisecond)
  }

  it should "return a proper HttpResponse when a normal request processed and committed" in {
    val (in, out0, out1) = getTestPorts

    val time = "2017-08-15T13:49:55Z"
    var data = s"""
                      |{
                      |  "id": "123",
                      |  "title": "user.login",
                      |  "actor": {"id": "123", "objectType": "user"},
                      |  "published": "$time"
                      |}
      """.stripMargin

    out1.request(1)
    out0.request(1)

    in.sendNext(HttpRequest(entity = HttpEntity(data)))

    out0.expectNoMsg(100.millisecond)

    var event = out1.expectNext()
    event.metadata shouldEqual EventMetaData(
      "123",
      "user.login",
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(time).getTime,
      None,
      Some("123")
    )
    event.body shouldEqual EventBody(ByteString(data), DataFormat.ACTIVITYSTREAMS)
    event.context("responsePromise").asInstanceOf[Promise[HttpResponse]].success(successfulResponse)

    val res = out0.expectNext()
    res.status shouldEqual StatusCodes.OK

    in.expectRequest()
  }

  // TODO: test abnormal cases

  private def getTestPorts: (TestPublisher.Probe[HttpRequest],
                             TestSubscriber.Probe[HttpResponse],
                             TestSubscriber.Probe[Event]) = {

    val settings = HttpSourceSettings("0.0.0.0", 80, DataFormat.ACTIVITYSTREAMS, 1000, 10)

    val in = TestPublisher.probe[HttpRequest]()
    val out0 = TestSubscriber.probe[HttpResponse]()

    val httpBind = Source.single(
      Flow.fromSinkAndSourceCoupled(Sink.fromSubscriber(out0), Source.fromPublisher(in))
    )
    val h = new HttpSource(settings, Some(httpBind))
    val out1 = h.getGraph().toMat(TestSink.probe[Event])(Keep.right).run()

    (in, out0, out1)

  }
}
