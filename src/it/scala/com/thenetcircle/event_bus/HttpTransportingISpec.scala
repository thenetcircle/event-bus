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

package com.thenetcircle.event_bus

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.util.ByteString
import com.thenetcircle.event_bus.transporter.{Transporter, TransporterSettings}

import scala.concurrent.Future

class HttpTransportingISpec extends BaseIntegrationISpec {

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val transporter = Transporter(
      TransporterSettings(system.settings.config.getConfig("event-bus.runtime.test-transporter"))
    )
    transporter.run()
  }

  behavior of "HttpTransporting"

  it should "send to emitter successfully by proper request content" in {
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(
        HttpRequest(
          uri = "http://localhost:8080",
          entity = HttpEntity("""
              |{
              |  "title": "user.login",
              |  "actor": {"id": "123", "objectType": "user"}
              |}
          """.stripMargin)
        )
      )

    responseFuture
      .flatMap(httpResponse => {
        assert(httpResponse.status == StatusCodes.OK)
        httpResponse.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      })
      .map(body => assert(body.utf8String == "ok"))
  }

  it should "send failed by non json content" in {
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(HttpRequest(uri = "http://localhost:8080", entity = HttpEntity("""
              |test
            """.stripMargin)))

    responseFuture
      .flatMap(httpResponse => {
        assert(httpResponse.status == StatusCodes.BadRequest)
        httpResponse.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      })
      .map(body => assert(body.utf8String == "ko"))
  }

  it should "send failed by missing fields content" in {
    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(
        HttpRequest(uri = "http://localhost:8080", entity = HttpEntity("""
                                |{"title":"user.login"}}
                              """.stripMargin))
      )

    responseFuture
      .flatMap(httpResponse => {
        assert(httpResponse.status == StatusCodes.BadRequest)
        httpResponse.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      })
      .map(body => assert(body.utf8String == "ko"))
  }

}
