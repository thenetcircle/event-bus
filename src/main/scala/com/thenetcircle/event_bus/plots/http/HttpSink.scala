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

package com.thenetcircle.event_bus.plots.http

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Sink}
import akka.stream.stage._
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.interface.ISink
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class HttpSinkSettings(host: String,
                            port: Int,
                            maxRetryTimes: Int,
                            defaultRequest: HttpRequest,
                            // TODO: set to optional
                            expectedResponse: Option[String] = None)

class HttpSink(settings: HttpSinkSettings,
               connectionPool: Flow[(HttpRequest, Event), (Try[HttpResponse], Event), _],
               fallbacker: Sink[Event, _])(implicit val materializer: Materializer)
    extends ISink
    with StrictLogging {

  implicit val executionContext: ExecutionContext =
    materializer.executionContext

  val sender: Flow[Event, (Try[HttpResponse], Event), NotUsed] =
    Flow[Event].map(buildRequest).via(connectionPool)

  def buildRequest(event: Event): (HttpRequest, Event) = {
    val request =
      settings.defaultRequest.withEntity(HttpEntity(event.body.data))

    logger.debug(s"Sending new request to EndPoint")

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

  override def inputGraph: Flow[Event, Event, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val retryEngine =
        builder.add(new HttpRetryEngine[Event](settings.maxRetryTimes, responseChecker))

      /** --- work flow --- */
      // format: off

      /** check if the result is expected, otherwise will retry */

      retryEngine.ready ~> sender ~>  retryEngine.result
                                      retryEngine.failed ~> fallbacker

      // format on

      FlowShape(retryEngine.incoming, retryEngine.succeed)
    })
  
}

object HttpSink extends StrictLogging {

  def apply(_config: Config)(implicit system: ActorSystem, materializer: Materializer): HttpSink = {
    val config: Config =
      _config.withFallback(system.settings.config.getConfig("event-bus.sink.http"))

    logger.info(s"Creating a new HttpSinkSettings according to config: $config")

    try {
      val requestMethod = if (config.hasPath("request.method")) {
        config.getString("request.method").toUpperCase() match {
          case "POST" => HttpMethods.POST
          case "GET"  => HttpMethods.GET
          case unacceptedMethod =>
            throw new IllegalArgumentException(
              s"Http request method $unacceptedMethod is unsupported."
            )
        }
      } else {
        HttpMethods.POST
      }
      val requsetUri =
        if (config.hasPath("request.uri")) Uri(config.getString("request.uri"))
        else Uri./

      val defaultRequest: HttpRequest = HttpRequest(method = requestMethod, uri = requsetUri)

      val expectedResponse =
        if (config.hasPath("expected-response-data"))
          Some(config.getString("expected-response-data"))
        else None

      val settings = HttpSinkSettings(
        config.getString("request.host"),
        config.getInt("request.port"),
        config.getInt("max-retry-times"),
        defaultRequest,
        expectedResponse
      )

      val rootConfig =
        system.settings.config
      val connectionPoolSettings: ConnectionPoolSettings =
        if (config.hasPath("akka.http.host-connection-pool")) {
          ConnectionPoolSettings(
            config
                .withFallback(rootConfig)
          )
        } else {
          ConnectionPoolSettings(rootConfig)
        }

      // TODO: check when it creates a new pool
      val connectionPool = Http().cachedHostConnectionPool[Event](
        settings.host,
        settings.port,
        connectionPoolSettings)

      // TODO: implementation of fallbacker
      val fallbacker: Sink[Event, NotUsed] = Flow[Event].to(Sink.ignore)

      new HttpSink(settings ,connectionPool, fallbacker)

    } catch {
      case ex: Throwable =>
        logger.error(s"Creating HttpSinkSettings failed with error: ${ex.getMessage}")
        throw ex
    }
  }

}


final class HttpRetryEngine[T <: Event](
                                           maxRetryTimes: Int,
                                           responseChecker: (HttpResponse, T) => Future[Boolean]
                                       )(implicit executionContext: ExecutionContext)
    extends GraphStage[HttpRetryEngineShape[T, (Try[HttpResponse], T), T, T, T]]
{
  val incoming: Inlet[T]          = Inlet("incoming")
  val result: Inlet[(Try[HttpResponse], T)] = Inlet("result")
  val ready: Outlet[T]            = Outlet("ready")
  val succeed: Outlet[T]          = Outlet("succeed")
  val failed: Outlet[T]           = Outlet("failed")

  override def shape: HttpRetryEngineShape[T, (Try[HttpResponse], T), T, T, T] =
    HttpRetryEngineShape(incoming, result, ready, succeed, failed)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with StageLogging {
      val retryTimes: AtomicInteger = new AtomicInteger(0)

      val isPending: AtomicBoolean = new AtomicBoolean(false)
      var isWaitingPull: Boolean = false

      setHandler(incoming, new InHandler {
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
      })

      setHandler(ready, new OutHandler {
        override def onPull() = {
          log.debug("onPull ready")
          if (!isPending.get()) {
            log.debug("tryPull incoming")
            tryPull(incoming)
          }
          else {
            log.debug("set waitingPull to true")
            isWaitingPull = true
          }
        }
      })

      setHandler(result, new InHandler {
        override def onPush() = {
          log.debug("onPush result")
          grab(result) match {
            case (responseTry, event) =>
              responseTry match {
                case Success(response) =>
                  responseChecker(response, event).onComplete(getAsyncCallback(responseHandler(event)).invoke)

                case Failure(ex) =>
                  log.error(
                    s"Event ${event.metadata.name} sent failed with error: ${ex.getMessage}.")
                  failureHandler(event)
              }
          }
        }
      })

      setHandler(failed, new OutHandler {
        override def onPull() = {
          log.debug("onPull failed")
          if (!hasBeenPulled(result) && isAvailable(succeed)) {
            log.debug("tryPull result")
            tryPull(result)
          }
        }
      })

      setHandler(succeed, new OutHandler {
        override def onPull() = {
          log.debug("onPull succeed")
          if (!hasBeenPulled(result) && isAvailable(failed)) {
            log.debug("tryPull result")
            tryPull(result)
          }
        }
      })

      def responseHandler(event: T): (Try[Boolean]) => Unit = {
        case Success(true) =>
          pushResultTo(succeed, event)
        case Success(false) =>
          failureHandler(event)
        case Failure(ex) =>
          log.error(s"Parse response error: ${ex.getMessage}")
          pushResultTo(failed, event)
      }

      def failureHandler(event: T): Unit = {
        if (retryTimes.incrementAndGet() >= maxRetryTimes) {
          log.error(s"Event sent failed after retried $maxRetryTimes times.")
          pushResultTo(failed, event)
        }
        else {
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
    }
}

final case class HttpRetryEngineShape[Incoming, Result, Ready, Succeed, Failed](
                                                                                   incoming: Inlet[Incoming],
                                                                                   result: Inlet[Result],
                                                                                   ready: Outlet[Ready],
                                                                                   succeed: Outlet[Succeed],
                                                                                   failed: Outlet[Failed]
                                                                               ) extends Shape {

  override val inlets:  Seq[Inlet[_]] = incoming :: result :: Nil
  override val outlets: Seq[Outlet[_]] = ready :: succeed :: failed :: Nil

  override def deepCopy(): Shape = HttpRetryEngineShape(incoming, result, ready, succeed, failed)
}