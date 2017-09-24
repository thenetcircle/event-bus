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
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Directives._
import com.thenetcircle.event_bus.dispatcher.{Dispatcher, DispatcherSettings}
import com.thenetcircle.event_bus.transporter.{Transporter, TransporterSettings}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.collection.mutable
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success}

class HttpDispatchingISpec extends BaseIntegrationISpec with StrictLogging {

  var receiver: Future[Http.ServerBinding]            = _
  val resultListeners: mutable.Queue[Promise[String]] = mutable.Queue.empty

  def addListener(listener: Promise[String]): Unit =
    resultListeners.enqueue(listener)

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val transporter = Transporter(
      TransporterSettings(
        system.settings.config.getConfig("event-bus-runtime.test-transporter")))
    transporter.run()

    val dispatcher = Dispatcher(
      DispatcherSettings(
        ConfigFactory
          .parseString(s"""
                         |{
                         |  pipeline.outlet-settings {
                         |    group-id = EventBus.TestDispatcher.${System
                            .currentTimeMillis()}
                         |  }
                         |}
                       """.stripMargin)
          .withFallback(system.settings.config
            .getConfig("event-bus-runtime.test-dispatcher"))))
    dispatcher.run()

    val route = path("") {
      post {
        entity(as[String])(body => {
          while (resultListeners.nonEmpty) {
            resultListeners.dequeue().success(body)
          }
          Directives.complete(HttpEntity("ok"))
        })
      }
    }
    receiver = Http().bindAndHandle(route, "localhost", 8081)

    Thread.sleep(2000) // waiting for consumer ready
  }

  override protected def afterAll(): Unit = {
    Thread.sleep(2000) // waiting for requests done
    receiver
      .map(binding => {
        binding.unbind()
      })
      .map(_ => Http().shutdownAllConnectionPools())
      .map(_ => super.afterAll())
  }

  behavior of "HttpDispatching"

  it should "in the end get same request on EndPoint side" in {

    val result = Promise[String]
    addListener(result)

    val requestBody =
      """
        |{
        |  "verb": "user.login",
        |  "actor": {"id": "123", "objectType": "user"}
        |}
      """.stripMargin

    val responseFuture: Future[HttpResponse] = Http().singleRequest(
      HttpRequest(
        uri = "http://localhost:8080",
        entity = HttpEntity(requestBody)
      ))

    responseFuture.onComplete {
      case Success(response) => response.discardEntityBytes()
      case Failure(ex)       => println(ex.getMessage)
    }

    result.future.map(r => {
      assert(r == requestBody)
    })

  }

  ignore should "get same result according the input order with same actor" in {

    val result: mutable.ListBuffer[String] = mutable.ListBuffer.empty
    val resultFuture                       = Promise[mutable.ListBuffer[String]]

    val testCount = 30

    for (i <- 1 to testCount) {
      val listener = Promise[String]
      addListener(listener)

      val requestBody =
        s"""
          |{
          |  "id": "$i",
          |  "verb": "user.login",
          |  "actor": {"id": "123", "objectType": "user"}
          |}
        """.stripMargin

      val responseFuture: Future[HttpResponse] = Http().singleRequest(
        HttpRequest(
          uri = "http://localhost:8080",
          entity = HttpEntity(requestBody)
        ))

      responseFuture.onComplete {
        case Success(response) => response.discardEntityBytes()
        case Failure(ex)       => logger.error(ex.getMessage)
      }

      listener.future.onComplete {
        case Success(r) =>
          logger.info(s" ---- ${result.size}")
          result.synchronized {
            result += r
            logger.info(s"${result.size}")
            if (result.size == testCount) resultFuture.success(result)
          }
        case Failure(ex) => logger.error(ex.getMessage)
      }
    }

    val r = Await.result(resultFuture.future, 10.seconds)

    for (i <- 1 to testCount) {
      assert(r(i) == s"""
                        |{
                        |  "id": "$i",
                        |  "verb": "user.login",
                        |  "actor": {"id": "123", "objectType": "user"}
                        |}
                       """.stripMargin)
    }

    assert(r.size == testCount)

  }

  // it should "use last offset if it crashes" in {}

}
