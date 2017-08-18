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

import akka.NotUsed
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
import com.thenetcircle.event_bus.extractor.{ExtractedData, Extractor}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success}

case class HttpEntryPointSettings(
    name: String,
    interface: String,
    port: Int
)

class HttpEntryPoint(
    settings: HttpEntryPointSettings
)(implicit system: ActorSystem,
  materializer: Materializer,
  extractor: Extractor[EventFormat])
    extends EntryPoint {

  private val serverSource
    : Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
    Http().bind(interface = settings.interface, port = settings.port)

  override val port
    : Source[Source[Event, NotUsed], Future[Http.ServerBinding]] =
    serverSource
      .map(connection => {

        Source.fromGraph(GraphDSL.create() {
          implicit builder =>
            import GraphDSL.Implicits._

            val requestFlow = builder.add(connection.flow)
            val connectionHandler =
              builder.add(new HttpEntryPoint.ConnectionHandler())
            val unpackFlow = Flow[Future[HttpResponse]].mapAsync(1)(identity)

            // ----- work flow -----
            // format: off
            
            requestFlow.out ~> connectionHandler.in

                               connectionHandler.out0 ~> unpackFlow ~> requestFlow
            
            // format: on

            SourceShape(connectionHandler.out1)
        })

      })
      .named(s"entrypoint-${settings.name}")

}

object HttpEntryPoint {

  val successfulResponse = HttpResponse(entity = HttpEntity("ok"))

  def apply[Fmt <: EventFormat: Extractor](
      settings: HttpEntryPointSettings
  )(implicit system: ActorSystem, materializer: Materializer): HttpEntryPoint =
    new HttpEntryPoint(settings)

  /** A stage with one inlet and two outlets, When [[HttpRequest]] come in inlet
    *  Will create a [[Future]] of [[HttpResponse]] to outlet0 and a [[Event]] to outlet1
    *  After the [[Event]] got committed, The Future of HttpResponse will be completed
    *
    *  {{{
    *                              +------------+
    *             In[HttpRequest] ~~>           |
    *                              |           ~~> Out1[Event]
    *  Out0[Future[HttpResponse]] <~~           |
    *                              +------------+
    *  }}}
    */
  final class ConnectionHandler()(implicit materializer: Materializer,
                                  extractor: Extractor[EventFormat])
      extends GraphStage[FanOutShape2[HttpRequest, Future[HttpResponse], Event]] {

    implicit val _ec: ExecutionContextExecutor = materializer.executionContext

    val in: Inlet[HttpRequest]             = Inlet("inlet-http-request")
    val out0: Outlet[Future[HttpResponse]] = Outlet("outlet-http-response")
    val out1: Outlet[Event]                = Outlet("outlet-event")

    override def shape: FanOutShape2[HttpRequest, Future[HttpResponse], Event] =
      new FanOutShape2(in, out0, out1)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        setHandler(
          in,
          new InHandler {
            override def onPush(): Unit =
              push(out0, requestPreprocess(grab(in)))
          }
        )

        setHandler(out0, new OutHandler {
          override def onPull(): Unit =
            if (!hasBeenPulled(in))
              pull(in)
        })

        setHandler(out1, new OutHandler {
          override def onPull(): Unit =
            if (!hasBeenPulled(in))
              pull(in)
        })

        def requestPreprocess(request: HttpRequest): Future[HttpResponse] = {
          val responsePromise = Promise[HttpResponse]
          val strictEntity    = request.entity.toStrict(3.seconds)(materializer)
          val result =
            strictEntity.flatMap(entity => extractor.extract(entity.data))

          result.onComplete {
            case Success(extractedData) =>
              getEventExtractCallback(responsePromise).invoke(extractedData)
            case Failure(e) =>
              responsePromise.success(
                HttpResponse(StatusCodes.BadRequest,
                             entity = HttpEntity(e.getMessage)))
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
              extractedData.priority.getOrElse(EventPriority.Normal),
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
