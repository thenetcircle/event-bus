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
import akka.http.scaladsl.model._
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Sink}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.thenetcircle.event_bus.Event
import com.typesafe.scalalogging.StrictLogging

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

// Notice that each new instance will create a new connection pool based on the poolSettings
class HttpEndPoint(
    val settings: HttpEndPointSettings,
    connectionPool: Flow[(HttpRequest, Event), (Try[HttpResponse], Event), _])(
    implicit val materializer: Materializer)
    extends EndPoint
    with StrictLogging {

  implicit val ec: ExecutionContext = materializer.executionContext

  private val retryEngine =
    new HttpEndPoint.RetryEngine(settings.maxRetryTimes)

  private val sender =
    Flow[Event]
      .map(event => {
        settings.defaultRequest
          .withEntity(HttpEntity(event.body.data)) -> event
      })
      .via(connectionPool)

  private val resChecker
    : Flow[(Try[HttpResponse], Event), (Boolean, Event), NotUsed] =
    Flow[(Try[HttpResponse], Event)]
      .map {
        case (responseTry, event) =>
          responseTry match {
            // if response succeed, checks the body
            case Success(response) =>
              response.entity
                .toStrict(3.seconds)
                .map(entity =>
                  if (checkResponse(response, entity)) {
                    logger.error(
                      s"Event  ${event.metadata.name} sent failed with unexpected response: ${entity.data}.")
                    (false, event)
                  } else {
                    (true, event)
                })
            case Failure(ex) =>
              logger.error(
                s"Event ${event.metadata.name} sent failed with error: ${ex.getMessage}.")
              Future.successful((false, event))
          }
      }
      // TODO: take care of failed cases
      .mapAsync(1)(identity)

  // TODO: complete fallbacker
  private val fallbacker: Sink[Event, NotUsed] = Flow[Event].to(Sink.ignore)

  private def checkResponse(response: HttpResponse,
                            entity: HttpEntity.Strict): Boolean = {
    response.status
      .isSuccess() && entity.data.utf8String == settings.expectedResponseData
  }

  override def port: Flow[Event, Event, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      /** --- work flow --- */
      // format: off

                   /** check if the result is expected, otherwise will retry */

      retryEngine.ready ~> sender ~> resChecker ~> retryEngine.result
                                                   retryEngine.failed ~> fallbacker

      // format on

      FlowShape(retryEngine.incoming, retryEngine.succeed)
  })
}

object HttpEndPoint {

  def apply(settings: HttpEndPointSettings)(
      implicit system: ActorSystem, materializer: Materializer): HttpEndPoint = {
    // TODO: check when it creates a new pool
    val connectionPool = Http().cachedHostConnectionPool[Event](
      settings.host,
      settings.port,
      settings.poolSettings)

    new HttpEndPoint(settings, connectionPool)
  }

  final class RetryEngine(maxRetryTimes: Int) extends GraphStage[RetryEngineShape[Event, (Boolean, Event), Event, Event, Event]]
  {
    type T = Event

    val incoming: Inlet[T]                    = Inlet("incoming")
    val result: Inlet[(Boolean, T)] = Inlet("result")
    val ready: Outlet[T]   = Outlet("ready")
    val succeed: Outlet[T]   = Outlet("succeed")
    val failed: Outlet[T]   = Outlet("failed")

    override def shape: RetryEngineShape[T, (Boolean, T), T, T, T] =
      RetryEngineShape(incoming, result, ready, succeed, failed)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {
        private val retryFieldName = "retry-times"

        override def preStart(): Unit = tryPull(incoming)

        setHandler(incoming, new InHandler {
          override def onPush() = push(ready, grab(incoming))
        })

        setHandler(result, new InHandler {
          override def onPush() = {
            grab(result) match {
              case (true, event) => push(succeed, event)  // pull(incoming)
              case (false, event) =>
                val retryTimes =
                  if (event.hasContext(retryFieldName))
                    event.context(retryFieldName).asInstanceOf[Int]
                  else
                    0

                if (retryTimes >= maxRetryTimes)
                  push(failed, event)
                else
                  push(ready, event.addContext(retryFieldName, retryTimes + 1))
            }
          }
        })

        setHandler(ready, new OutHandler {
          override def onPull() = tryPull(result)
        })

        setHandler(succeed, new OutHandler {
          override def onPull() = tryPull(incoming)
        })

        setHandler(failed, new OutHandler {
          override def onPull() = tryPull(incoming)
        })
      }
  }

  final case class RetryEngineShape[Incoming, Result, Ready, Succeed, Failed](
    incoming: Inlet[Incoming],
    result: Inlet[Result],
    ready: Outlet[Ready],
    succeed: Outlet[Succeed],
    failed: Outlet[Failed]
  ) extends Shape {

    override val inlets:  Seq[Inlet[_]] = incoming :: result :: Nil
    override val outlets: Seq[Outlet[_]] = ready :: succeed :: failed :: Nil

    override def deepCopy(): Shape = RetryEngineShape(incoming, result, ready, succeed, failed)

  }


}
