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

package com.thenetcircle.event_bus.transporter.entrypoint

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Source}
import akka.stream.stage._
import com.github.levkhomich.akka.tracing.TracingAnnotations
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.event_extractor.{
  EventExtractor,
  ExtractedData
}
import com.thenetcircle.event_bus.tracing.{
  TempTracingMessage,
  Tracing,
  TracingMessage
}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

class HttpEntryPoint(
    val settings: HttpEntryPointSettings,
    httpBindSource: Source[Flow[HttpResponse, HttpRequest, Any], _]
)(implicit val system: ActorSystem,
  materializer: Materializer,
  eventExtractor: EventExtractor)
    extends EntryPoint
    with StrictLogging
    with Tracing {

  logger.debug(s"new HttpEntryPoint ${settings.name} is created")

  private val loggerFlow = Flow[HttpRequest].map(request => {
    logger.debug(s"new Request $request")
    request
  })

  override val stream: Source[Event, _] =
    httpBindSource
      .flatMapMerge(
        settings.maxConnections,
        httpHandlerFlow => {
          Source.fromGraph(GraphDSL.create() {
            implicit builder =>
              import GraphDSL.Implicits._

              val httpHandlerFlowShape = builder.add(httpHandlerFlow)
              val connectionHandler =
                builder.add(new HttpEntryPoint.ConnectionHandler(this))
              val unpackFlow = Flow[Future[HttpResponse]].mapAsync(
                settings.perConnectionParallelism)(identity)

              /** ----- work flow ----- */
              // format: off
              // since Http().bind using join, The direction is a bit different
            
              httpHandlerFlowShape.out ~> loggerFlow ~> connectionHandler.in

                                                        connectionHandler.out0 ~> unpackFlow ~> httpHandlerFlowShape.in
            
              // format: on

              SourceShape(connectionHandler.out1)
          })
        }
      )
      .named(s"entrypoint-${settings.name}")
}

object HttpEntryPoint {

  val failedResponse =
    HttpResponse(StatusCodes.BadRequest, entity = HttpEntity("ko"))
  val successfulResponse = HttpResponse(entity = HttpEntity("ok"))

  def apply(settings: HttpEntryPointSettings)(
      implicit system: ActorSystem,
      materializer: Materializer,
      eventExtractor: EventExtractor): HttpEntryPoint = {
    val httpBindSource = Http()
      .bind(
        interface = settings.interface,
        port = settings.port,
        settings = settings.serverSettings
      )
      .map(_.flow)

    new HttpEntryPoint(settings, httpBindSource)
  }

  /** Does transform a incoming [[HttpRequest]] to a [[Future]] of [[HttpResponse]]
    * and a [[Event]] with a committer to complete the [[Future]]
    *
    * {{{
    *                             +------------+
    *            In[HttpRequest] ~~>           |
    *                             |           ~~> Out1[Event]
    * Out0[Future[HttpResponse]] <~~           |
    *                             +------------+
    * }}}
    *
    * '''Emits when'''
    *   a incoming [[HttpRequest]] successful transformed to a [[Event]],
    *   the Future of [[HttpResponse]] will always emit to out0
    *
    * '''Backpressures when''' any of the outputs backpressure
    *
    * '''Completes when''' upstream completes
    *
    * '''Cancels when''' when any downstreams cancel
    */
  final class ConnectionHandler(entryPoint: HttpEntryPoint)(
      implicit system: ActorSystem,
      materializer: Materializer,
      eventExtractor: EventExtractor)
      extends GraphStage[FanOutShape2[HttpRequest, Future[HttpResponse], Event]] {

    implicit val executionContext: ExecutionContext =
      materializer.executionContext

    val unmarshaller: Unmarshaller[HttpEntity, ExtractedData] =
      Unmarshaller.byteStringUnmarshaller.andThen(Unmarshaller.apply(_ =>
        data => eventExtractor.extract(data)))

    val in: Inlet[HttpRequest]             = Inlet("inlet-http-request")
    val out0: Outlet[Future[HttpResponse]] = Outlet("outlet-http-response")
    val out1: Outlet[Event]                = Outlet("outlet-event")

    override def shape: FanOutShape2[HttpRequest, Future[HttpResponse], Event] =
      new FanOutShape2(in, out0, out1)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with StageLogging with Tracing {

        def tryPullIn(): Unit =
          if (!hasBeenPulled(in) && isAvailable(out0) && isAvailable(out1)) {
            log.debug("tryPull in")
            tryPull(in)
          }

        setHandler(
          in,
          new InHandler {
            override def onPush(): Unit = {
              log.debug("onPush in -> push out0")
              push(out0, requestPreprocess(grab(in)))
            }
          }
        )

        setHandler(out0, new OutHandler {
          override def onPull(): Unit = {
            log.debug("onPull out0")
            tryPullIn()
          }
        })

        setHandler(out1, new OutHandler {
          override def onPull(): Unit = {
            log.debug("onPull out1")
            tryPullIn()
          }
        })

        def requestPreprocess(request: HttpRequest): Future[HttpResponse] = {
          val tracingMessage = TempTracingMessage(
            Tracing.createNewTracingId(request),
            "Request"
          )
          tracer.sample(tracingMessage, "EventBus")

          val responsePromise = Promise[HttpResponse]

          val extractedDataFuture =
            unmarshaller.apply(request.entity)(executionContext, materializer)
          val callback =
            getEventExtractingCallback(tracingMessage, responsePromise)
          extractedDataFuture.onComplete(result => callback.invoke(result))

          responsePromise.future
        }

        def getEventExtractingCallback(tracingMessage: TracingMessage,
                                       responsePromise: Promise[HttpResponse])
          : AsyncCallback[Try[ExtractedData]] =
          getAsyncCallback[Try[ExtractedData]](extractedDataTry => {
            tracer.record(tracingMessage,
                          TracingAnnotations.Custom("Extracted"))
            extractedDataTry match {
              case Success(extractedData) =>
                val event = Event(
                  extractedData.metadata,
                  extractedData.body,
                  extractedData.channel.getOrElse(
                    ChannelResolver.getChannel(extractedData.metadata)),
                  EventSourceType.Http,
                  tracingMessage.tracingId
                ).withCommitter(
                  () => {
                    tracer.record(tracingMessage,
                                  TracingAnnotations.Custom("Transported"))
                    // tracer.flush(tracingMessage)

                    responsePromise.success(successfulResponse).future
                  }
                )

                log.debug("push out1")
                push(out1, event)

              case Failure(ex) =>
                log.info(
                  s"Request send failed with error message: ${ex.getMessage}")
                tracer.record(tracingMessage, ex)
                tracer.flush(tracingMessage)
                responsePromise.success(failedResponse)
                tryPullIn()
            }
          })

      }

  }

}
