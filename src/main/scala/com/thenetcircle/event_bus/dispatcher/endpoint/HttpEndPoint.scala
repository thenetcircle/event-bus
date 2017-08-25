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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.RequestEntityAcceptance.Expected
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.directives.BasicDirectives
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Partition, Sink}
import akka.util.ByteString
import com.thenetcircle.event_bus.Event
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

// Notice that each new instance will create a new connection pool based on the poolSettings
class HttpEndPoint(
    val settings: HttpEndPointSettings,
    connectionPool: Flow[(HttpRequest, Event), (Try[HttpResponse], Event), _])(
    implicit val system: ActorSystem)
    extends EndPoint
    with StrictLogging {

  override def port: Flow[Event, Event, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val inlet = builder.add(Flow[Event])

      val sender =
        Flow[Event]
          .map(event => {
            settings.defaultRequest
              .withEntity(HttpEntity(event.body.data)) -> event
          })
          .via(connectionPool)

      val resChecker: Flow[(Try[HttpResponse], Event), Event, NotUsed] =
        Flow[(Try[HttpResponse], Event)]
          .map {
            case (responseTry, event) =>
              responseTry match {
                case Success(response) =>
                  response.entity
                    .toStrict(3.seconds)
                    .map(
                      entity =>
                        event
                          .addContext("http-response", response)
                          .addContext(
                            "http-response-result",
                            (response.status.isSuccess() && checkResponseData(
                              entity.data)).toString))
                case Failure(ex) =>
                  logger.error(
                    s"Event ${event.metadata.name} sent failed with error: ${ex.getMessage}.")
                  Future(event.addContext("http-response-result", "false"))
              }
          }
          // TODO: take care of failed cases
          .mapAsync(1)(identity)

      val resultRouter =
        builder.add(Partition[(Event, Boolean)](2, {
          case (event, result) =>
            // success checked response will go to outlet 0, failed go to 1
            if (result) 0 else 1
        }))

      val fallbacker: Sink[Event, NotUsed] = Flow[Event].to(Sink.ignore)

      val outlet = builder.add(Flow[Event])

      /** --- work flow --- */
      // format: off

                              /** check if the result is expected, otherwise will retry */

                inlet  ~>      sender     ~>  resChecker
                                                    resChecker.out(0)  ~>  eventPicker  ~>  outlet // will ack to pipeline
                                                    resChecker.out(1)  ~>  eventPicker  ~>  fallbacker

      // format on

      FlowShape(inlet.in, outlet.out)
  })
  
  private def checkResponseData(data: ByteString): Boolean =
    data.utf8String == settings.expectedResponseData

  private def eventPicker: Flow[(Try[HttpResponse], Event), Event, NotUsed] =
    Flow[(Try[HttpResponse], Event)].map(_._2)

}

object HttpEndPoint {

  /** custom post method with idempotent set to true, it makes connectionPool retry if the requests failed */
  val IPOST: HttpMethod =
    HttpMethod.custom("POST", safe = false, idempotent = true, requestEntityAcceptance = Expected)

  def apply(settings: HttpEndPointSettings)(
      implicit system: ActorSystem): HttpEndPoint = {
    // TODO: check when it creates a new pool
    val connectionPool = Http().cachedHostConnectionPool[Event](
      settings.host,
      settings.port,
      settings.poolSettings)

    new HttpEndPoint(settings, connectionPool)
  }
}
