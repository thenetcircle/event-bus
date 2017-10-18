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

package com.thenetcircle.event_bus.transporter.entrypoint

import java.text.SimpleDateFormat

import akka.http.scaladsl.model.{
  HttpEntity,
  HttpRequest,
  HttpResponse,
  StatusCodes
}
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.TestSink
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.util.ByteString
import com.thenetcircle.event_bus.event_extractor._
import com.thenetcircle.event_bus.testkit.AkkaStreamSpec
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.event_extractor.EventFormat.DefaultFormat

import scala.concurrent.duration._

class HttpEntryPointSpec extends AkkaStreamSpec {

  implicit val eventExtractor = EventExtractor(DefaultFormat)

  behavior of "HttpEntryPoint"

  it should "return a BadRequest of HttpResponse when empty request coming" in {
    val (in, out0, out1) = getEntryPointPorts

    out1.request(1)
    out0.request(1)

    in.sendNext(HttpRequest())

    out1.expectNoMsg(100.millisecond)

    val res = out0.expectNext(100.millisecond)
    res.status shouldEqual StatusCodes.BadRequest
  }

  it should "return a BadRequest of HttpResponse when a unaccepted request body coming" in {
    val (in, out0, out1) = getEntryPointPorts

    out1.request(1)
    out0.request(1)
    // sub2.request(1)
    in.sendNext(HttpRequest(entity = HttpEntity(s"""
                                                       |{
                                                       |  "verb": "user.login"
                                                       |}
                                                    """)))

    val res = out0.expectNext()
    res.status shouldEqual StatusCodes.BadRequest
    out1.expectNoMsg(100.millisecond)
  }

  it should "return a proper HttpResponse when a normal request processed and committed" in {
    val (in, out0, out1) = getEntryPointPorts

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
      Some(EventActor("123", "user"))
    )
    event.body shouldEqual EventBody(ByteString(data), DefaultFormat)

    out1.request(1) // after process, request new one before commit

    event.committer.foreach(_.commit())

    val res = out0.expectNext()
    res.status shouldEqual StatusCodes.OK

    in.expectRequest()
  }

  // TODO: test abnormal cases

  private def getEntryPointPorts: (TestPublisher.Probe[HttpRequest],
                                   TestSubscriber.Probe[HttpResponse],
                                   TestSubscriber.Probe[Event]) = {
    val settings = HttpEntryPointSettings(
      "test-entrypoint",
      EntryPointPriority.Normal,
      1000,
      10,
      EventFormat.DefaultFormat,
      ServerSettings(system),
      "127.0.0.1",
      8888
    )

    val in   = TestPublisher.probe[HttpRequest]()
    val out0 = TestSubscriber.probe[HttpResponse]()

    val handler = Source.single(
      Flow.fromSinkAndSourceCoupled(Sink.fromSubscriber(out0),
                                    Source.fromPublisher(in)))
    val hep  = new HttpEntryPoint(settings, handler)
    val out1 = hep.stream.toMat(TestSink.probe[Event])(Keep.right).run()

    (in, out0, out1)
  }

  /*private def getConnectionHandlerPorts
    : (TestPublisher.Probe[HttpRequest],
       TestSubscriber.Probe[Future[HttpResponse]],
       TestSubscriber.Probe[Event]) = {

    val source = TestSource.probe[HttpRequest]
    val sink1  = TestSink.probe[Future[HttpResponse]]
    val sink2  = TestSink.probe[Event]

    val (in, out0, out1) =
      RunnableGraph
        .fromGraph(GraphDSL.create(source, sink1, sink2)((_, _, _)) {
          implicit builder => (p1, p2, p3) =>
            import GraphDSL.Implicits._

            val handler =
              builder.add(new HttpEntryPoint.ConnectionHandler())

            // format: off
            p1 ~> handler.in
            handler.out0 ~> p2
            handler.out1 ~> p3
            // format: on

            ClosedShape
        })
        .run()

    (in, out0, out1)
  }*/

}
