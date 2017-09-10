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

package com.thenetcircle.event_bus.testkit

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.{ConnectionPoolSettings, ServerSettings}
import akka.stream.{FlowShape, Materializer}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink, Source}
import akka.util.ByteString
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.EventPriority.EventPriority
import com.thenetcircle.event_bus.dispatcher.endpoint.{
  HttpEndPoint,
  HttpEndPointSettings
}
import com.thenetcircle.event_bus.pipeline.PipelineInlet
import com.thenetcircle.event_bus.pipeline.kafka.KafkaPipelineFactory
import com.thenetcircle.event_bus.transporter.{Transporter, TransporterSettings}
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointPriority.EntryPointPriority
import com.thenetcircle.event_bus.transporter.entrypoint._
import com.typesafe.config.ConfigFactory

import scala.util.{Success, Try}

object TestComponentBuilder {

  def createTestEvent(name: String = "TestEvent",
                      time: Long = 111,
                      sourceType: EventSourceType = EventSourceType.Http,
                      priority: EventPriority = EventPriority.Normal,
                      body: String = "body",
                      format: EventFormat = EventFormat.DefaultFormat): Event =
    Event(
      EventMetaData("uuid", name, time, "publisher", ("user", "222")),
      EventBody(ByteString(body), format),
      "channel",
      sourceType,
      priority
    )

  def createHttpEndPointSettings(
      name: String = "TestHttpEndPoint",
      host: String = "localhost",
      port: Int = 8888,
      maxRetryTimes: Int = 10,
      defaultRequest: HttpRequest = HttpRequest(),
      expectedResponse: Option[String] = None
  )(implicit system: ActorSystem): HttpEndPointSettings = HttpEndPointSettings(
    name,
    host,
    port,
    maxRetryTimes,
    ConnectionPoolSettings(system),
    defaultRequest,
    expectedResponse
  )

  def createHttpEndPoint(
      expectedResponse: Option[String] = None,
      fallbacker: Sink[Event, _] = Sink.ignore,
      maxRetryTimes: Int = 1,
      defaultRequest: HttpRequest = HttpRequest(),
      defaultResponse: Try[HttpResponse] = Success(HttpResponse())
  )(sender: Flow[(HttpRequest, Event), (Try[HttpResponse], Event), _] =
      Flow[(HttpRequest, Event)].map {
        case (_, event) =>
          (defaultResponse, event)
      })(implicit system: ActorSystem,
         materializer: Materializer): HttpEndPoint = {
    val endPointSettings = createHttpEndPointSettings(
      maxRetryTimes = maxRetryTimes,
      defaultRequest = defaultRequest,
      expectedResponse = expectedResponse
    )
    new HttpEndPoint(endPointSettings, sender, fallbacker)
  }

  def createTransporter(
      testSources: Vector[(EntryPointPriority, Source[Event, _])],
      testPipelinePort: Vector[Sink[Event, _]],
      commitParallelism: Int = 1,
      transportParallelism: Int = 1)(
      implicit system: ActorSystem,
      materializer: Materializer): Transporter = {
    val entryPointSettings = HttpEntryPointSettings(
      "TestHttpEntryPoint",
      EntryPointPriority.Normal,
      100,
      10,
      EventFormat.DefaultFormat,
      ServerSettings(system),
      "localhost",
      8888
    )

    val testEntryPoints = testSources.map(_source =>
      new EntryPoint {
        override val settings: EntryPointSettings = new EntryPointSettings {
          override val name           = s"TestEntryPoint-${_source._1}"
          override val priority       = _source._1
          override val entryPointType = EntryPointType.HTTP
          override val eventFormat    = EventFormat.DefaultFormat
        }

        override def port: Source[Event, _] = _source._2
    })

    val testPipelineLeftPort = new PipelineInlet {
      var currentIndex = 0
      override def stream: Flow[Event, Event, NotUsed] = {
        val testPP = testPipelinePort(currentIndex)
        val flow   = createFlowFromSink(testPP)
        currentIndex += 1
        flow
      }
    }

    val settings = TransporterSettings(
      "TestTransporter",
      commitParallelism,
      transportParallelism,
      Vector(entryPointSettings),
      KafkaPipelineFactory().getPipeline("TestPipeline"),
      KafkaPipelineFactory().getPipelineInletSettings(ConfigFactory.empty()),
      None
    )

    new Transporter(settings, testEntryPoints, () => testPipelineLeftPort)

  }

  def createFlowFromSink(sink: Sink[Event, _]): Flow[Event, Event, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val inlet     = builder.add(Flow[Event])
      val outlet    = builder.add(Flow[Event])
      val broadcast = builder.add(Broadcast[Event](2))

      // format: off
    inlet ~> broadcast
    broadcast.out(0) ~> sink
    broadcast.out(1) ~> outlet
    // format: on

      FlowShape(inlet.in, outlet.out)
    })

}
