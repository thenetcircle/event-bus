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
import akka.{Done, NotUsed}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Flow, GraphDSL, MergeHub, Source}
import akka.http.scaladsl.model.HttpMethods._
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, OverflowStrategy, SourceShape}
import com.thenetcircle.event_bus.extractor.Extractor
import com.thenetcircle.event_bus.{Event, EventFormat}

import scala.concurrent.duration._
import scala.collection.mutable
import scala.concurrent.{Await, Future, Promise}

case class HttpEntryPointSettings(
    interface: String,
    port: Int
) extends EntryPointSettings

class HttpEntryPoint[T: Extractor](settings: HttpEntryPointSettings) extends EntryPoint(settings) {

  val extractor: Extractor[T] = implicitly[Extractor[T]]

  class HttpEntryPointShape(connection: IncomingConnection) extends GraphStage[SourceShape[Event]] {

    val out: Outlet[Event] = Outlet("outlet")

    override def shape: SourceShape[Event] = SourceShape(out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        private val buffer = mutable.Queue.empty[Event]

        override def preStart(): Unit =
          connection.handleWithSyncHandler { request =>
            response
          }

        setHandler(out, new OutHandler {
          override def onPull(): Unit =
            if (buffer.nonEmpty)
              push(out, buffer.dequeue())
        })
      }
  }

  val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
    Http().bind(interface = settings.interface, port = settings.port)

  val a = serverSource.map( connection =>
    Source.fromGraph(GraphDSL.create(){ implicit builder =>
      val flow = Flow[HttpRequest]

      SourceShape()
    })
  )

  lazy val bindingFuture: Future[Http.ServerBinding] =
    serverSource
      .runForeach { connection =>
        val handler: HttpRequest => Future[HttpResponse] = {
          case r: HttpRequest =>
        }

        Flow[HttpRequest].map()
        connection.handleWithAsyncHandler(handler)
      }

  val mergehub = MergeHub.source(16)

  Source.fromPublisher()

  override def port: Source[Source[Event, NotUsed], NotUsed] = ???

  private def getEventFromRequest(request: HttpRequest, response: Promise[HttpResponse]): Event = {
    val committer = () => {
      response.success(HttpResponse()).future
    }

    val data = request.entity.toStrict(3.seconds)

    data.onComplete( d =>
    val (metadata, channel, priority) = extractor.extract(d)

    Event(
    )
      )

  }

}
