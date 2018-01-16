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

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCode, _}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge}
import akka.stream.stage._
import akka.util.ByteString
import com.thenetcircle.event_bus.event.{Event, EventStatus}
import com.thenetcircle.event_bus.interface.SinkTask
import com.thenetcircle.event_bus.story.TaskRunningContext
import com.typesafe.scalalogging.StrictLogging

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class HttpSinkSettings(host: String,
                            port: Int,
                            maxRetryTimes: Int,
                            defaultRequest: HttpRequest,
                            maxConnections: Int = 4,
                            minConnections: Int = 0,
                            connectionMaxRetries: Int = 5,
                            maxOpenRequests: Int = 32,
                            pipeliningLimit: Int = 1,
                            idleTimeout: String = "30 s")

class HttpSink(val settings: HttpSinkSettings,
               responseCheckFunc: (StatusCode, Seq[HttpHeader], String) => Boolean)
    extends SinkTask
    with StrictLogging {

  def createRequest(event: Event): HttpRequest = {
    settings.defaultRequest.withEntity(HttpEntity(event.body.data))
  }

  def getConnectionPoolSettings(): ConnectionPoolSettings =
    ConnectionPoolSettings(s"""akka.http.host-connection-pool {
                              |  max-connections = ${settings.maxConnections}
                              |  min-connections = ${settings.minConnections}
                              |  max-retries = ${settings.connectionMaxRetries}
                              |  max-open-requests = ${settings.maxOpenRequests}
                              |  pipelining-limit = ${settings.pipeliningLimit}
                              |  idle-timeout = ${settings.idleTimeout}
                              |}""".stripMargin)

  def sendAndCheck()(
      implicit context: TaskRunningContext
  ): Flow[(HttpRequest, Event), Try[Event], NotUsed] = {}

  override def getHandler()(
      implicit context: TaskRunningContext
  ): Flow[Event, Try[Event], NotUsed] = {

    implicit val system: ActorSystem = context.getActorSystem()
    implicit val materializer: Materializer = context.getMaterializer()

    val sender: Flow[(HttpRequest, Event), (Try[HttpResponse], Event), NotUsed] =
      Http().superPool[Event](settings = getConnectionPoolSettings()).map {
        case (Success(httpResponse), event) =>
          if (checkResponse(httpResponse)) Success(event)
          else Failure(new Exception("response from the endpoint is not expected."))
        case f => f
      }

    Flow[Event]
      .map(_event => (createRequest(_event), _event))
      .via(sender)

  }

  class RetrySender(
      maxRetryTimes: Int,
      respCheckFunc: (StatusCode, Seq[HttpHeader], String) => Boolean,
      conntionPoolSettings: ConnectionPoolSettings
  )(implicit context: TaskRunningContext)
      extends Actor
      with ActorLogging {

    import akka.pattern.pipe

    implicit val system: ActorSystem = context.getActorSystem()
    implicit val materializer: Materializer = context.getMaterializer()
    implicit val executionContext: ExecutionContext = context.getExecutionContext()

    override def receive: Receive = {
      case RetrySender.Send(_request, retryTimes) =>
        Http()
          .singleRequest(request = _request, settings = conntionPoolSettings)
          .map(_response => (_request, _response, retryTimes))
          .pipeTo(self)(sender = sender())

      case (request: HttpRequest, HttpResponse(status, headers, entity, _), retryTimes) =>
        entity.dataBytes.runFold(ByteString(""))(_ ++ _).map { body =>
          log.debug("Got response, body: " + body.utf8String)
          respCheckFunc(status, headers, body.utf8String)
        }

      case (resp @ HttpResponse(code, _, _, _), retryTimes) =>
        log.info("Request failed, response code: " + code)
        resp.discardEntityBytes()
    }
  }

  object RetrySender {
    case class Send(request: HttpRequest, retryTimes: Int = 1)
  }
}
