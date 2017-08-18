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

import akka.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse, StatusCodes }
import akka.stream.ClosedShape
import akka.stream.scaladsl.{ GraphDSL, RunnableGraph }
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import akka.util.ByteString
import com.thenetcircle.event_bus.EventFormat.DefaultFormat
import com.thenetcircle.event_bus.base.AkkaTestCase
import com.thenetcircle.event_bus.{ Event, EventBody, EventMetaData }

import scala.concurrent.duration.{ Duration, _ }
import scala.concurrent.{ Await, Future }

class HttpEntryPointTest extends AkkaTestCase {

  test("online test") {
    val hep = HttpEntryPoint[DefaultFormat](
      HttpEntryPointSettings("test", "127.0.0.1", 8888)
    )

    val port = hep.port.runForeach(
      s =>
        s.runForeach(event => {
          println(event.toString)
          event.committer.get.commit()
        })
    )

    Await.ready(_system.whenTerminated, Duration.Inf)
  }

  test("test ConnectionHandler") {

    val source = TestSource.probe[HttpRequest]
    val sink1 = TestSink.probe[Future[HttpResponse]]
    val sink2 = TestSink.probe[Event]

    val (pub, sub1, sub2) =
      RunnableGraph
        .fromGraph(GraphDSL.create(source, sink1, sink2)((_, _, _)) { implicit builder => (p1, p2, p3) =>
          import GraphDSL.Implicits._

          val handler = builder.add(new HttpEntryPoint.ConnectionHandler())

          p1 ~> handler.in
          handler.out0 ~> p2
          handler.out1 ~> p3

          ClosedShape
        })
        .run()

    /**  ----  Bad Request Test ---- */
    // have to make demands first
    sub1.request(1)
    sub2.request(1)
    pub.sendNext(HttpRequest())
    var result = Await.result(sub1.expectNext(), 3.seconds)
    result.status shouldEqual StatusCodes.BadRequest
    sub2.expectNoMsg(100.millisecond)

    /**  ----  Bad Request Test2 ---- */
    sub1.request(1)
    // sub2.request(1)
    pub.sendNext(HttpRequest(entity = HttpEntity(s"""
                                                    |{
                                                    |  "verb": "user.login"
                                                    |}
                                                    """)))
    result = Await.result(sub1.expectNext(), 3.seconds)
    result.status shouldEqual StatusCodes.BadRequest
    sub2.expectNoMsg(100.millisecond)

    /**  ----  Good Request Test1 ---- */
    sub1.request(1)
    // sub2.request(1)
    val time = "2017-08-15T13:49:55Z"
    var data = s"""
         |{
         |  "id": "123",
         |  "verb": "user.login",
         |  "actor": {"id": "123", "objectType": "user"},
         |  "published": "$time"
         |}
      """.stripMargin
    pub.sendNext(HttpRequest(entity = HttpEntity(data)))

    var event = sub2.expectNext()
    event.metadata shouldEqual EventMetaData("123",
                                             "user.login",
                                             new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(time).getTime,
                                             "",
                                             "123" -> "user")
    event.body shouldEqual EventBody(ByteString(data), DefaultFormat)
    event.committer.foreach(_.commit())

    result = Await.result(sub1.expectNext(), 3.seconds)
    result.status shouldEqual StatusCodes.OK

  }

}
