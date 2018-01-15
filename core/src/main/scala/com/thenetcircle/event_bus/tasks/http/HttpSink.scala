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
                            expectedResponse: Option[String] = None,
                            connectionPoolSettingsOption: Option[ConnectionPoolSettings] = None)

class HttpSink(
    val settings: HttpSinkSettings,
    overriddenSendingFlow: Option[Flow[(HttpRequest, Event), (Try[HttpResponse], Event), _]] = None
)(implicit context: TaskRunningContext)
    extends SinkTask
    with StrictLogging {

  implicit val system: ActorSystem = context.getActorSystem()
  implicit val materializer: Materializer = context.getMaterializer()
  implicit val executor: ExecutionContext = context.getExecutor()

  // TODO: double check when it creates a new pool
  private val sendingFlow =
    overriddenSendingFlow.getOrElse(settings.connectionPoolSettingsOption match {
      case Some(_poolSettings) =>
        Http().cachedHostConnectionPool[Event](settings.host, settings.port, _poolSettings)
      case None => Http().cachedHostConnectionPool[Event](settings.host, settings.port)
    })

  private val sender: Flow[Event, (Try[HttpResponse], Event), NotUsed] =
    Flow[Event]
      .map(event => {
        // build request from settings
        val request =
          settings.defaultRequest.withEntity(HttpEntity(event.body.data))
        logger.debug(s"Sending new request to EndPoint")
        (request, event)
      })
      .via(sendingFlow)

  private def checkResponse(statusCode: StatusCode, response: String): Boolean = {
    statusCode.isSuccess() && (settings.expectedResponse match {
      case Some(ep) => response == ep
      case None     => true
    })
  }

  override def getGraph(): Flow[Event, Event, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val input = builder.add(Flow[Event])
      val retryEngine =
        builder.add(new HttpRetryEngine[Event](settings.maxRetryTimes, checkResponse))
      val output = builder.add(Merge[Event](2))
      val failureHandler = builder.add(Flow[Event].map(e => e.withStatus(EventStatus.FAILED)))

      // workflow (check if the result is expected, otherwise will retry)
      // format: off

            input.out ~> retryEngine.incoming
                         retryEngine.ready  ~> sender ~> retryEngine.result
                                                         retryEngine.succeed ~> output.in(0)
                                                         retryEngine.failed  ~> failureHandler ~> output.in(1)

      // format: on

      FlowShape(input.in, output.out)

    })
}

final class HttpRetryEngine[T <: Event](
    maxRetryTimes: Int,
    responseCheckFunc: (StatusCode, String) => Boolean
)(implicit executor: ExecutionContext)
    extends GraphStage[HttpRetryEngineShape[T, (Try[HttpResponse], T), T, T, T]] {
  val incoming: Inlet[T] = Inlet("incoming")
  val result: Inlet[(Try[HttpResponse], T)] = Inlet("result")
  val ready: Outlet[T] = Outlet("ready")
  val succeed: Outlet[T] = Outlet("succeed")
  val failed: Outlet[T] = Outlet("failed")

  override def shape: HttpRetryEngineShape[T, (Try[HttpResponse], T), T, T, T] =
    HttpRetryEngineShape(incoming, result, ready, succeed, failed)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {
      val retryTimes: AtomicInteger = new AtomicInteger(0)

      val isPending: AtomicBoolean = new AtomicBoolean(false)
      var isWaitingPull: Boolean = false

      setHandler(
        incoming,
        new InHandler {
          override def onPush() = {
            log.debug(s"onPush incoming -> push ready")
            push(ready, grab(incoming))
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
              tryPull(incoming)
            } else {
              log.debug("set waitingPull to true")
              isWaitingPull = true
            }
          }
        }
      )

      setHandler(
        result,
        new InHandler {
          override def onPush() = {
            log.debug("onPush result")
            grab(result) match {
              case (responseTry, event) =>
                responseTry match {
                  case Success(response) =>
                    checkResponse(response, event).onComplete(
                      getAsyncCallback(responseHandler(event)).invoke
                    )

                  case Failure(ex) =>
                    log.error(
                      s"Event ${event.metadata.name} sent failed with error: ${ex.getMessage}."
                    )
                    failureHandler(event)
                }
            }
          }
        }
      )

      setHandler(
        failed,
        new OutHandler {
          override def onPull() = {
            log.debug("onPull failed")
            if (!hasBeenPulled(result) && isAvailable(succeed)) {
              log.debug("tryPull result")
              tryPull(result)
            }
          }
        }
      )

      setHandler(
        succeed,
        new OutHandler {
          override def onPull() = {
            log.debug("onPull succeed")
            if (!hasBeenPulled(result) && isAvailable(failed)) {
              log.debug("tryPull result")
              tryPull(result)
            }
          }
        }
      )

      def responseHandler(event: T): (Try[Boolean]) => Unit = {
        case Success(true) =>
          pushResultTo(succeed, event)
        case Success(false) =>
          failureHandler(event)
        case Failure(ex) =>
          log.warning(s"Parse response error: ${ex.getMessage}")
          pushResultTo(failed, event)
      }

      def failureHandler(event: T): Unit = {
        if (retryTimes.incrementAndGet() >= maxRetryTimes) {
          log.warning(s"Event sent failed after retried $maxRetryTimes times.")
          pushResultTo(failed, event)
        } else {
          log.debug("emit ready & tryPull result")
          emit(ready, event)
          tryPull(result)
        }
      }

      def pushResultTo(outlet: Outlet[T], result: T): Unit = {
        log.debug(s"push result $result to $outlet")
        push(outlet, result)
        isPending.set(false)
        retryTimes.set(0)
        if (isClosed(incoming)) completeStage()
        else if (isWaitingPull) tryPull(incoming)
      }

      def checkResponse(response: HttpResponse, event: Event): Future[Boolean] = {
        response.entity
          .toStrict(3.seconds)(materializer)
          .map { entity =>
            val result: Boolean = responseCheckFunc(response.status, entity.data.utf8String)
            if (!result && retryTimes.get() == 0) {
              log.warning(
                s"""Event "${event.uniqueName}" sent failed with unexpected response: ${entity.data.utf8String}."""
              )
            }
            result
          }
      }
    }
}

final case class HttpRetryEngineShape[Incoming, Result, Ready, Succeed, Failed](
    incoming: Inlet[Incoming],
    result: Inlet[Result],
    ready: Outlet[Ready],
    succeed: Outlet[Succeed],
    failed: Outlet[Failed]
) extends Shape {

  override val inlets: Seq[Inlet[_]] = incoming :: result :: Nil
  override val outlets: Seq[Outlet[_]] = ready :: succeed :: failed :: Nil

  override def deepCopy(): Shape = HttpRetryEngineShape(incoming, result, ready, succeed, failed)
}
