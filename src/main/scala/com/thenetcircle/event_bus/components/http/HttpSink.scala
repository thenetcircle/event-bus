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

package com.thenetcircle.event_bus.components.http

import akka.NotUsed
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.{FlowShape, Materializer}
import akka.stream.scaladsl.{Flow, GraphDSL, Sink}
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.dispatcher.emitter.HttpEmitter
import com.thenetcircle.event_bus.interface.ISink
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try

case class HttpSinkSettings(name: String,
                            host: String,
                            port: Int,
                            maxRetryTimes: Int,
                            // TODO: set pool maxRetries to 1
                            connectionPoolSettings: ConnectionPoolSettings,
                            defaultRequest: HttpRequest,
                            // TODO: set to optional
                            expectedResponse: Option[String] = None)

class HttpSink(settings: HttpSinkSettings,
               connectionPool: Flow[(HttpRequest, Event), (Try[HttpResponse], Event), _],
               fallbacker: Sink[Event, _])(implicit val materializer: Materializer)
    extends ISink
    with StrictLogging {

  logger.info(s"new HttpEmitter ${settings.name} is created")

  implicit val executionContext: ExecutionContext =
    materializer.executionContext

  val sender: Flow[Event, (Try[HttpResponse], Event), NotUsed] =
    Flow[Event].map(buildRequest).via(connectionPool)

  def buildRequest(event: Event): (HttpRequest, Event) = {
    val request =
      settings.defaultRequest.withEntity(HttpEntity(event.body.data))

    logger.debug(s"Sending new request to Emitter")

    (request, event)
  }

  def responseChecker(response: HttpResponse, event: Event): Future[Boolean] = {
    response.entity
    // TODO: timeout is too much?
    // TODO: unify ExecutionContext
    // TODO: use Unmarshaller
      .toStrict(3.seconds)
      .map { entity =>
        val result: Boolean = {
          response.status.isSuccess() && (settings.expectedResponse match {
            case Some(expectedResponse) =>
              entity.data.utf8String == expectedResponse
            case None => true
          })
        }

        if (!result) {
          logger.warn(
            s"Event (${event.metadata} - ${event.channel}) sent failed " +
              s"to ${settings.defaultRequest.protocol.value}://${settings.host}:${settings.port}${settings.defaultRequest.uri} " +
              s"with unexpected response: ${entity.data.utf8String}."
          )
          false
        } else {
          true
        }
      }
  }

  override def stream: Flow[Event, Event, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val retryEngine =
        builder.add(new HttpEmitter.HttpRetryEngine[Event](settings.maxRetryTimes, responseChecker))

      /** --- work flow --- */
      // format: off

      /** check if the result is expected, otherwise will retry */

      retryEngine.ready ~> sender ~>  retryEngine.result
      retryEngine.failed ~> fallbacker

      // format on

      FlowShape(retryEngine.incoming, retryEngine.succeed)
    })
  
}
