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
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.testkit.AkkaTestSpec
import com.thenetcircle.event_bus.testkit.createTestEvent

import scala.util.Success

class HttpEndPointSpec extends AkkaTestSpec {

  behavior of "HttpEndPoint"

  it must "delivery successfully to the target" in {
    val endPointSettings = HttpEndPointSettings(
      "TestHttpEndPoint",
      "localhost",
      8888,
      1,
      expectedResponseData = "OK",
      ConnectionPoolSettings(actorSystem),
      HttpRequest()
    )

    val sender = Flow[(HttpRequest, Event)].map {
      case (request, event) => (Success(HttpResponse()), event)
    }

    val endPoint = new HttpEndPoint(endPointSettings, sender, Sink.ignore)

    val testSource = TestSource.probe[Event]
    val testSink   = TestSink.probe[Event]

    val (pub, sub) = testSource
      .viaMat(endPoint.port)(Keep.left)
      .toMat(testSink)(Keep.both)
      .run()

    val testEvent = createTestEvent()
    pub.sendNext(testEvent)
    sub.expectNext(testEvent)
  }

}
