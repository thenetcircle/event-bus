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
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
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

  def checkResponse(
      response: HttpResponse
  )(implicit context: TaskRunningContext): Future[Boolean] = {
    implicit val executionContext: ExecutionContext = context.getExecutionContext()

    response.entity.dataBytes
      .runFold(ByteString(""))(_ ++ _)
      .map(body => responseCheckFunc(response.status, response.headers, body.utf8String))
  }

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
}

object Retry {

  def apply(maxRetryTimes: Int, checkFunc: (HttpResponse) => Future[Boolean])(
      logic: Flow[(HttpRequest, Event), (Try[HttpResponse], Event), NotUsed]
  ): Flow[(HttpRequest, Event), (Try[HttpResponse], Event), NotUsed] = {
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val retry = builder.add(new RetryStage[Event](maxRetryTimes, checkFunc))

      // workflow (check if the result is expected, otherwise will retry)
      // format: off

      retry.ready ~> logic ~> retry.checkpoint

      // format: on

      FlowShape(retry.input, retry.output)

    })
  }

  final class RetryStage[I, O, P](maxRetryTimes: Int, checkFunc: (O) => Future[Boolean])(
      implicit executionContext: ExecutionContext
  ) extends GraphStage[RetryShape[(I, P), (Try[O], P), (I, P), (Try[O], P)]] {

    val input: Inlet[(I, P)] = Inlet("input")
    val checkpoint: Inlet[(Try[O], P)] = Inlet("checkpoint")
    val ready: Outlet[(I, P)] = Outlet("ready")
    val output: Outlet[(Try[O], P)] = Outlet("output")

    override def shape: RetryShape[(I, P), (Try[O], P), (I, P), (Try[O], P)] =
      RetryShape(input, checkpoint, ready, output)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with StageLogging {
        val retryTimes: AtomicInteger = new AtomicInteger(0)

        val isPending: AtomicBoolean = new AtomicBoolean(false)
        var isWaitingPull: Boolean = false

        setHandler(
          input,
          new InHandler {
            override def onPush() = {
              log.debug(s"onPush incoming -> push ready")
              push(ready, grab(input))
              isPending.set(true)
            }

            override def onUpstreamFinish() = {
              log.debug("onUpstreamFinish")
              if (!isPending.get()) {
                log.debug("completeStage")
                completeStage()
              }
            }
          }
        )

        setHandler(
          ready,
          new OutHandler {
            override def onPull() = {
              log.debug("onPull ready")
              if (!isPending.get()) {
                log.debug("tryPull incoming")
                tryPull(input)
              } else {
                log.debug("set waitingPull to true")
                isWaitingPull = true
              }
            }
          }
        )

        setHandler(
          checkpoint,
          new InHandler {
            override def onPush() = {
              log.debug("onPush result")
              grab(checkpoint) match {
                case (resultTry, payload) =>
                  resultTry match {
                    case Success(result) =>
                      checkFunc(result).onComplete(
                        getAsyncCallback(checkCallback(resultTry, payload)).invoke
                      )
                    case Failure(ex) =>
                      log.error(s"check failed with error: $ex.")
                      processFailure(payload)
                  }
              }
            }
          }
        )

        setHandler(output, new OutHandler {
          override def onPull() = {
            log.debug("onPull output")
            if (!hasBeenPulled(checkpoint)) {
              log.debug("tryPull checker")
              tryPull(checkpoint)
            }
          }
        })

        def checkCallback(resultTry: Try[O], payload: P): (Try[Boolean]) => Unit = {
          case Success(true) =>
            pushResult((resultTry, payload))
          case Success(false) =>
            processFailure(payload)
          case Failure(ex) =>
            log.warning(s"Parse response error: ${ex.getMessage}")
            pushResult((resultTry, payload))
        }

        def processFailure(payload: P): Unit = {
          if (retryTimes.incrementAndGet() >= maxRetryTimes) {
            log.warning(s"Event sent failed after retried $maxRetryTimes times.")
            pushResult(payload)
          } else {
            log.debug("emit ready & tryPull result")
            emit(ready, payload)
            tryPull(checkpoint)
          }
        }

        def pushResult(result: (Try[O], P)): Unit = {
          push(output, result)
          isPending.set(false)
          retryTimes.set(0)
          if (isClosed(input)) completeStage()
          else if (isWaitingPull) tryPull(input)
        }
      }
  }

  final case class RetryShape[A, B, C, D](input: Inlet[A],
                                          checkpoint: Inlet[B],
                                          ready: Outlet[C],
                                          output: Outlet[D])
      extends Shape {

    override val inlets: Seq[Inlet[_]] = input :: checkpoint :: Nil
    override val outlets: Seq[Outlet[_]] = ready :: output :: Nil

    override def deepCopy(): Shape = RetryShape(input, checkpoint, ready, output)
  }

}
