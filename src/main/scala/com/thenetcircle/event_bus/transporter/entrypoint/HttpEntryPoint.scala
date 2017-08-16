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
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.{ HttpEntity, HttpRequest, HttpResponse, StatusCodes }
import akka.stream._
import akka.stream.scaladsl.{ Flow, Source }
import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import com.thenetcircle.event_bus.EventFormat.DefaultFormat
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.extractor.Extractor

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success }

case class HttpEntryPointSettings(
    interface: String,
    port: Int
) extends EntryPointSettings

class HttpEntryPoint(settings: HttpEntryPointSettings)(implicit system: ActorSystem, materializer: Materializer)
    extends EntryPoint(settings) {

  implicit val ec = system.dispatcher

  val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
    Http().bind(interface = settings.interface, port = settings.port)

  override val port: Source[Source[Event, NotUsed], Future[Http.ServerBinding]] =
    serverSource.map(connection => Source.fromGraph(new HttpEntryPointGraph(connection)))

}

object HttpEntryPoint {

  private class ConnectionHandler()(implicit extractor: Extractor[EventFormat])
      extends GraphStage[FanOutShape2[HttpRequest, Future[HttpResponse], Event]] {
    val in: Inlet[HttpRequest] = Inlet("inlet-http-request")
    val out0: Outlet[Future[HttpResponse]] = Outlet("outlet-http-response")
    val out1: Outlet[Event] = Outlet("outlet-event")

    override def shape: FanOutShape2[HttpRequest, Future[HttpResponse], Event] = new FanOutShape2(in, out0, out1)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        setHandler(
          in,
          new InHandler {
            override def onPush(): Unit = {
              val request = grab(in)
              val (response, event) = requestPreprocess(request)

              push(out0, response)

              if (event.isDefined)
                push(out1, event)
            }
          }
        )

        setHandler(out0, new OutHandler {
          override def onPull() = ???
        })

        setHandler(out1, new OutHandler {
          override def onPull() = ???
        })

        def requestPreprocess(request: HttpRequest): (Future[HttpResponse], Option[Event]) = {
          val responsePromise = Promise[HttpResponse]
          var event: Option[Event] = None
          val data = request.entity.toStrict(3.seconds)

          data.onComplete {
            case Success(entity) =>
              val body = entity.data
              try {
                val extractedData = extractor.extract(body)
                event = Some(
                  Event(
                    extractedData.metadata,
                    EventBody(body, extractor.dataFormat),
                    extractedData.channel.getOrElse(""),
                    EventSourceType.Http,
                    extractedData.priority.getOrElse(EventPriority.Normal),
                    Map.empty
                  ).withCommitter(() => {
                    responsePromise.success(HttpResponse())
                  })
                )
              } catch {
                case e: Throwable =>
                  responsePromise.success(HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(e.getMessage)))
              }

            case Failure(e) =>
              responsePromise.success(HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(e.getMessage)))
          }

          (responsePromise.future, event)
        }

      }
  }

}
