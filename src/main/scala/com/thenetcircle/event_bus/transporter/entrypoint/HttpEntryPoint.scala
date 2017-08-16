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
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.scaladsl.Source
import akka.stream.stage.{ GraphStage, GraphStageLogic, OutHandler }
import akka.stream.{ Attributes, Materializer, Outlet, SourceShape }
import com.thenetcircle.event_bus._

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

  private class HttpEntryPointGraph(connection: IncomingConnection, perConnectionBufferSize: Int = 16)
      extends GraphStage[SourceShape[Event]] {
    val out: Outlet[Event] = Outlet("outlet")
    override def shape: SourceShape[Event] = SourceShape(out)
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with OutHandler {

        private val maxBufferSize = perConnectionBufferSize
        private var buffer = mutable.Queue.empty[Event]

        override def preStart(): Unit = {
          val requestProcessor = getAsyncCallback[(HttpRequest, Promise[HttpResponse])] {
            case (request, responsePromise) =>
              val data = request.entity.toStrict(3.seconds)(materializer)

              data.onComplete {
                case Success(entity) =>
                  val body = entity.data
                  val extractedData = extractor.extract(body)
                  val event = Event(
                    extractedData.metadata,
                    EventBody(body, extractor.dataFormat),
                    extractedData.channel.getOrElse(""),
                    EventSourceType.Http,
                    extractedData.priority.getOrElse(EventPriority.Normal),
                    Map.empty
                  ).withCommitter(() => {
                    responsePromise.success(HttpResponse())
                  })

                  if (isAvailable(out))
                    push(out, event)
                  else if (buffer.size >= maxBufferSize)
                    responsePromise.failure(new Exception("buffer size exceed the maximum number."))
                  else
                    buffer.enqueue(event)

                case Failure(e) => responsePromise.failure(e)
              }
          }

          val handler = (request: HttpRequest) => {
            val responsePromise = Promise[HttpResponse]
            requestProcessor.invoke((request, responsePromise))
            responsePromise.future
          }

          connection.handleWithAsyncHandler(handler)(materializer)
        }

        override def onPull(): Unit =
          if (buffer.nonEmpty)
            push(out, buffer.dequeue())
      }
  }

  val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
    Http().bind(interface = settings.interface, port = settings.port)

  override val port: Source[Source[Event, NotUsed], Future[Http.ServerBinding]] =
    serverSource.map(connection => Source.fromGraph(new HttpEntryPointGraph(connection)))

}
