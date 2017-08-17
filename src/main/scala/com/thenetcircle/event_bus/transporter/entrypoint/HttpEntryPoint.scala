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
import akka.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse, StatusCodes }
import akka.stream._
import akka.stream.scaladsl.{ Flow, GraphDSL, Source }
import akka.stream.stage._
import akka.util.ByteString
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.extractor.Extractor

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

case class HttpEntryPointSettings(
    interface: String,
    port: Int
) extends EntryPointSettings

class HttpEntryPoint(settings: HttpEntryPointSettings)(implicit system: ActorSystem, materializer: Materializer)
    extends EntryPoint(settings) {

  implicit val _ec = system.dispatcher

  private val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
    Http().bind(interface = settings.interface, port = settings.port)

  override val port: Source[Source[Event, NotUsed], Future[Http.ServerBinding]] =
    serverSource
      .map(connection => {

        Source.fromGraph(GraphDSL.create() { implicit builder =>
          import GraphDSL.Implicits._

          val handlerFlowShape = builder.add(connection.flow)
          val connectionHandler = builder.add(new HttpEntryPoint.ConnectionHandler())

          handlerFlowShape.out ~> connectionHandler.in

          connectionHandler.out0 ~> Flow[Future[HttpResponse]]
            .mapAsync(1)(identity) ~> handlerFlowShape

          SourceShape(connectionHandler.out1)
        })

      })

}

object HttpEntryPoint {

  final class ConnectionHandler()(implicit system: ActorSystem,
                                  materializer: Materializer,
                                  extractor: Extractor[EventFormat])
      extends GraphStage[FanOutShape2[HttpRequest, Future[HttpResponse], Event]] {

    implicit val _ec = system.dispatcher

    val in: Inlet[HttpRequest] = Inlet("inlet-http-request")
    val out0: Outlet[Future[HttpResponse]] = Outlet("outlet-http-response")
    val out1: Outlet[Event] = Outlet("outlet-event")

    override def shape: FanOutShape2[HttpRequest, Future[HttpResponse], Event] = new FanOutShape2(in, out0, out1)

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
          override def onPull(): Unit = {}
        })

        setHandler(out1, new OutHandler {
          override def onPull(): Unit =
            pull(in)
        })

        def requestPreprocess(request: HttpRequest): Future[HttpResponse] = {
          val responsePromise = Promise[HttpResponse]
          val data = request.entity.toStrict(3.seconds)(materializer)
          val eventExtractCallback = getEventExtractCallback(responsePromise)

          data.onComplete {
            case Success(entity) =>
              eventExtractCallback.invoke(entity.data)
            case Failure(e) =>
              responsePromise.success(HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(e.getMessage)))
          }

          responsePromise.future
        }

        def getEventExtractCallback(responsePromise: Promise[HttpResponse]): AsyncCallback[ByteString] =
          getAsyncCallback(data => {
            try {
              val extractedData = extractor.extract(data)
              push(
                out1,
                Event(
                  extractedData.metadata,
                  EventBody(data, extractor.dataFormat),
                  extractedData.channel.getOrElse(""),
                  EventSourceType.Http,
                  extractedData.priority.getOrElse(EventPriority.Normal),
                  Map.empty
                ).withCommitter(
                  () =>
                    Future {
                      responsePromise.success(HttpResponse(entity = HttpEntity("event got consumer")))
                  }
                )
              )
            } catch {
              case NonFatal(e) =>
                responsePromise.success(HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(e.getMessage)))
            }
          })

      }
  }

}
