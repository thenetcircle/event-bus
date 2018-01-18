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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ServerSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition}
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.thenetcircle.event_bus.event.extractor.{
  DataFormat,
  EventExtractingException,
  EventExtractorFactory
}
import com.thenetcircle.event_bus.helper.ConfigStringParser
import com.thenetcircle.event_bus.interface.{SourceTask, SourceTaskBuilder}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class HttpSourceSettings(interface: String = "0.0.0.0",
                              port: Int = 8000,
                              format: DataFormat = DataFormat("ActivityStreams"),
                              succeededResponse: String = "ok",
                              serverSettings: ServerSettings)

class HttpSource(val settings: HttpSourceSettings) extends SourceTask with StrictLogging {

  def createResponseFromTry(result: Try[Any]): HttpResponse = result match {
    case Success(_) =>
      HttpResponse(entity = HttpEntity(settings.succeededResponse))

    case Failure(ex) =>
      HttpResponse(
        entity = HttpEntity(s"The request was processing failed with error ${ex.getMessage}.")
      )
  }

  def createResponseFromEvent(result: (Try[Done], Event)): HttpResponse = result match {
    case (s @ Success(_), event) => createResponseFromTry(s)
    case (f @ Failure(ex), _)    => createResponseFromTry(f)
  }

  def getRequestUnmarshallerHandler()(
      implicit materializer: Materializer,
      executionContext: ExecutionContext
  ): Flow[HttpRequest, Try[Event], NotUsed] = {
    val unmarshaller: Unmarshaller[HttpEntity, Event] =
      EventExtractorFactory.getHttpEntityUnmarshaller(settings.format)

    Flow[HttpRequest]
      .mapAsync(1)(request => {
        unmarshaller(request.entity)
          .map(event => Success(event))
          .recover {
            case ex: EventExtractingException =>
              logger.debug(s"A http request unmarshaller failed with error $ex")
              Failure(ex)
          }
      })
  }

  def getInternalHandler(handler: Flow[(Try[Done], Event), (Try[Done], Event), NotUsed])(
      implicit materializer: Materializer,
      executionContext: ExecutionContext
  ): Flow[HttpRequest, HttpResponse, NotUsed] = Flow.fromGraph(
    GraphDSL
      .create() { implicit builder =>
        import GraphDSL.Implicits._

        val unmarshaller = builder.add(getRequestUnmarshallerHandler())
        val partitioner = builder.add(Partition[Try[Event]](2, {
          case Success(_) => 0
          case Failure(_) => 1
        }))

        val realHandler = Flow[Try[Event]].map(t => (t.map(_ => Done), t.get)).via(handler)
        val response1 = Flow[(Try[Done], Event)].map(createResponseFromEvent)
        val response2 = Flow[Try[Any]].map(createResponseFromTry)

        val output = builder.add(Merge[HttpResponse](2))

        // format: off
        unmarshaller ~> partitioner
                        partitioner.out(0) ~> realHandler ~> response1 ~> output.in(0)
                        partitioner.out(1)                ~> response2 ~> output.in(1)
        // format: on

        FlowShape(unmarshaller.in, output.out)
      }
  )

  override def runWith(
      handler: Flow[(Try[Done], Event), (Try[Done], Event), NotUsed]
  )(implicit runningContext: TaskRunningContext): (KillSwitch, Future[Done]) = {
    implicit val system: ActorSystem = runningContext.getActorSystem()
    implicit val materializer: Materializer = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    val httpBindFuture =
      Http().bindAndHandle(
        handler = getInternalHandler(handler),
        interface = settings.interface,
        port = settings.port,
        settings = settings.serverSettings
      )

    val killSwitch = new KillSwitch {
      override def abort(ex: Throwable): Unit = shutdown()
      override def shutdown(): Unit = Await.ready(httpBindFuture.flatMap(_.unbind()), 5.seconds)
    }

    runningContext.addShutdownHook(killSwitch.shutdown())

    (killSwitch, httpBindFuture.map(_ => Done))
  }

}

class HttpSourceBuilder() extends SourceTaskBuilder with StrictLogging {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): HttpSource = {
    try {
      val config: Config =
        ConfigStringParser
          .convertStringToConfig(configString)
          .withFallback(buildingContext.getSystemConfig().getConfig("task.http-source"))

      val serverSettingsMap = config.as[Map[String, String]]("server")
      val serverSettings = {
        var _settingsStr =
          serverSettingsMap.foldLeft("")((acc, kv) => acc + "\n" + s"${kv._1} = ${kv._2}")
        ServerSettings(s"""akka.http.server {
                          |${_settingsStr}
                          |}""".stripMargin)
      }

      val settings = HttpSourceSettings(
        config.as[String]("interface"),
        config.as[Int]("port"),
        config.as[DataFormat]("format"),
        config.as[String]("succeeded-response"),
        serverSettings
      )

      new HttpSource(settings)
    } catch {
      case ex: Throwable =>
        logger.error(s"Build HttpSource failed with error: $ex")
        throw ex
    }
  }
}
