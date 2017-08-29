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
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Partition, Sink}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
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

      val inlet       = builder.add(Flow[Event])
      val retryEngine = new HttpEndPoint.RetryEngine[Event]()

      val sender =
        Flow[Event]
          .map(event => {
            settings.defaultRequest
              .withEntity(HttpEntity(event.body.data)) -> event
          })
          .via(connectionPool)

      val resChecker: Flow[(Try[HttpResponse], Event), (Int, Event), NotUsed] =
        Flow[(Try[HttpResponse], Event)]
          .map {
            case (responseTry, event) =>
              responseTry match {
                case Success(response) =>
                  response.entity
                    .toStrict(3.seconds)
                    .map(entity =>
                      if (response.status.isSuccess() && checkResponseData(
                            entity.data)) {
                        logger.error(
                          s"Event ${event.metadata.name} sent failed with response: ${entity.data}.")
                        (false, event)
                      } else {
                        (true, event.addContext("http-response", response))
                    })
                case Failure(ex) =>
                  logger.error(
                    s"Event ${event.metadata.name} sent failed with error: ${ex.getMessage}.")
                  Future.successful((false, event))
              }
          }
          // TODO: take care of failed cases
          .mapAsync(1)(identity)
          .map {
            case (result, event) =>
              if (result) {
                (0, event)
              } else {
                val retryTimes =
                  if (event.hasContext("retry-times"))
                    event.context("retry-times").asInstanceOf[Int] + 1
                  else 1

                if (retryTimes < settings.maxRetryTimes)
                  (1, event.addContext("retry-times", retryTimes))
                else
                  (2, event)
              }
          }

      val resultRouter                     = builder.add(Partition[(Int, Event)](3, _._1))
      val fallbacker: Sink[Event, NotUsed] = Flow[Event].to(Sink.ignore)
      val outlet                           = builder.add(Flow[Event])

      /** --- work flow --- */
      // format: off

                              /** check if the result is expected, otherwise will retry */

      retryEngine.out    ~> sender ~> resChecker ~> resultRouter
                                                    resultRouter.out(0)  ~>  eventPicker  ~>  outlet // will ack to pipeline
      retryEngine.result <~   eventPicker    <~     resultRouter.out(1)
                                                    resultRouter.out(2)  ~>  eventPicker  ~>  fallbacker

      // format on

      FlowShape(retryEngine.in, outlet.out)
  })
  
  private def checkResponseData(data: ByteString): Boolean =
    data.utf8String == settings.expectedResponseData

  private def eventPicker: Flow[(Int, Event), Event, NotUsed] =
    Flow[(Int, Event)].map(_._2)

}

object HttpEndPoint {

  def apply(settings: HttpEndPointSettings)(
      implicit system: ActorSystem): HttpEndPoint = {
    // TODO: check when it creates a new pool
    val connectionPool = Http().cachedHostConnectionPool[Event](
      settings.host,
      settings.port,
      settings.poolSettings)

    new HttpEndPoint(settings, connectionPool)
  }

  final class RetryEngine[T]() extends GraphStage[FanInShape2[T, (Boolean, Option[T]), T]]
  {
    val in: Inlet[T]     = Inlet("inlet")
    val result: Inlet[(Boolean, Option[T])] = Inlet("result-inlet")
    val out: Outlet[T]   = Outlet("outlet")

    override def shape: FanInShape2[T, (Boolean, Option[T]), T] = new FanInShape2(in, result, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        override def preStart(): Unit = pull(in)

        setHandler(in, new InHandler {
          override def onPush() = push(out, grab(in))
        })

        setHandler(result, new InHandler {
          override def onPush() = {
            grab(result) match {
              case (true, _) => pull(in)
              case (false, Some(payload)) => push(out, payload)
              case _ => throw new Exception("result is not correct.")
            }
          }
        })

        setHandler(out, new OutHandler {
          override def onPull() = pull(result)
        })
      }
  }
}
