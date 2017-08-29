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
import akka.http.scaladsl.model.{
  HttpEntity,
  HttpRequest,
  HttpResponse,
  StatusCodes
}
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Source}
import akka.stream.stage._
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.event_extractor.{
  EventExtractor,
  ExtractedData
}
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointPriority.EntryPointPriority

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success}

class HttpEntryPoint(
    val settings: HttpEntryPointSettings,
    httpBindSource: Source[Flow[HttpResponse, HttpRequest, Any], _]
)(implicit system: ActorSystem,
  materializer: Materializer,
  eventExtractor: EventExtractor)
    extends EntryPoint {

  override val port: Source[Event, _] =
    httpBindSource
      .flatMapMerge(
        settings.maxConnections,
        httpHandlerFlow => {
          Source.fromGraph(GraphDSL.create() {
            implicit builder =>
              import GraphDSL.Implicits._

              val httpHandlerFlowShape = builder.add(httpHandlerFlow)
              val connectionHandler = builder.add(
                new HttpEntryPoint.ConnectionHandler(settings.priority))
              val unpackFlow = Flow[Future[HttpResponse]].mapAsync(
                settings.perConnectionParallelism)(identity)

              /** ----- work flow ----- */
              // format: off
              // since Http().bind using join, The direction is a bit different
            
              httpHandlerFlowShape.out ~> connectionHandler.in

                                          connectionHandler.out0 ~> unpackFlow ~> httpHandlerFlowShape.in
            
              // format: on

              SourceShape(connectionHandler.out1)
          })
        }
      )
      .named(s"entrypoint-${settings.name}")
}

object HttpEntryPoint {

  val successfulResponse = HttpResponse(entity = HttpEntity("ok"))

  def apply(settings: HttpEntryPointSettings)(
      implicit system: ActorSystem,
      materializer: Materializer,
      eventExtractor: EventExtractor): HttpEntryPoint = {
    val serverSource = Http()
      .bind(interface = settings.interface, port = settings.port)
      .map(_.flow)
    new HttpEntryPoint(settings, serverSource)
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
  final class ConnectionHandler(entryPointPriority: EntryPointPriority)(
      implicit materializer: Materializer,
      eventExtractor: EventExtractor)
      extends GraphStage[FanOutShape2[HttpRequest, Future[HttpResponse], Event]] {

    implicit val _ec: ExecutionContextExecutor = materializer.executionContext

    val in: Inlet[HttpRequest]             = Inlet("inlet-http-request")
    val out0: Outlet[Future[HttpResponse]] = Outlet("outlet-http-response")
    val out1: Outlet[Event]                = Outlet("outlet-event")

    override def shape: FanOutShape2[HttpRequest, Future[HttpResponse], Event] =
      new FanOutShape2(in, out0, out1)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        def tryPullIn(): Unit =
          if (!hasBeenPulled(in) && isAvailable(out0) && isAvailable(out1))
            tryPull(in)

        setHandler(
          in,
          new InHandler {
            override def onPush(): Unit = {
              push(out0, requestPreprocess(grab(in)))
            }
          }
        )

        setHandler(out0, new OutHandler {
          override def onPull(): Unit = tryPullIn()
        })

        setHandler(out1, new OutHandler {
          override def onPull(): Unit = tryPullIn()
        })

        def requestPreprocess(request: HttpRequest): Future[HttpResponse] = {
          val responsePromise = Promise[HttpResponse]
          val strictEntity    = request.entity.toStrict(3.seconds)(materializer)
          val result =
            strictEntity.flatMap(entity => eventExtractor.extract(entity.data))

          result.onComplete {
            case Success(extractedData) =>
              getEventExtractCallback(responsePromise).invoke(extractedData)
            case Failure(e) =>
              // TODO failed response message
              responsePromise.success(
                HttpResponse(StatusCodes.BadRequest,
                             entity = HttpEntity(e.getMessage)))

              if (isAvailable(out1))
                tryPullIn()
          }

          responsePromise.future
        }

        def getEventExtractCallback(responsePromise: Promise[HttpResponse])
          : AsyncCallback[ExtractedData] =
          getAsyncCallback(extractedData => {
            val event = Event(
              extractedData.metadata,
              extractedData.body,
              extractedData.channel.getOrElse(
                ChannelResolver.getChannel(extractedData.metadata)),
              EventSourceType.Http,
              // Event priority equals entry points' priority and extracted priority from the data
              EventPriority(extractedData.priority.id + entryPointPriority.id),
              Map.empty
            ).withCommitter(
              () =>
                Future {
                  responsePromise.success(successfulResponse)
              }
            )

            push(out1, event)
          })

      }

  }

}
