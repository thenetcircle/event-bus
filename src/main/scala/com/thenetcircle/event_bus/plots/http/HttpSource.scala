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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.settings.ServerSettings
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Source}
import akka.stream.stage._
import com.thenetcircle.event_bus.event.extractor.{ExtractedData, ExtractorFactory, IExtractor}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.thenetcircle.event_bus.plots.http.HttpSource.{failedResponse, successfulResponse}
import com.thenetcircle.event_bus.story.interface.ISource
import com.thenetcircle.event_bus.tracing.Tracing
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

case class HttpSourceSettings(maxConnections: Int,
                              perConnectionParallelism: Int,
                              commitParallelism: Int,
                              format: DataFormat)

class HttpSource(
    settings: HttpSourceSettings,
    httpBind: Source[Flow[HttpResponse, HttpRequest, Any], _]
)(implicit executor: ExecutionContext)
    extends ISource
    with StrictLogging {

  implicit val extractor: IExtractor = ExtractorFactory.getExtractor(settings.format)

  override def graph: Source[Event, NotUsed] =
    httpBind
      .flatMapMerge(settings.maxConnections,
                    httpBindFlow => {
                      Source.fromGraph(GraphDSL.create() {
                        implicit builder =>
                          import GraphDSL.Implicits._

                          val client         = builder.add(httpBindFlow)
                          val requestHandler = builder.add(new HttpRequestHandler())

                          val unpackFlow =
                            Flow[Future[HttpResponse]]
                              .mapAsync(settings.perConnectionParallelism)(identity)

                          val debugFlow = Flow[HttpRequest].map(request => {
                            logger.debug(s"received a new Request")
                            request
                          })

                          /** ----- work flow ----- */
                          // since Http().bind using join, The direction is a bit different
                          // format: off

                          client ~> debugFlow ~> requestHandler.in

                                                 requestHandler.out0 ~> unpackFlow ~> client

                          // format: on

                          SourceShape(requestHandler.out1)
                      })
                    })
      .mapMaterializedValue(m => NotUsed)

  override def ackGraph: Flow[Event, Event, NotUsed] =
    Flow[Event]
      .filter(_.committer.isDefined)
      .mapAsync(settings.commitParallelism)(event => {
        event.committer.get
          .commit()
          .map(_ => {
            logger.debug(s"Event(${event.metadata.uuid}, ${event.metadata.name}) is committed.")
            event
          })
      })
}

object HttpSource extends StrictLogging {

  val failedResponse =
    HttpResponse(StatusCodes.BadRequest, entity = HttpEntity("ko"))
  val successfulResponse = HttpResponse(entity = HttpEntity("ok"))

  def apply(config: Config)(implicit system: ActorSystem,
                            materializer: Materializer,
                            executor: ExecutionContext): HttpSource = {
    try {

      val mergedConfig: Config =
        config.withFallback(system.settings.config.getConfig("event-bus.source.http"))

      val settings = HttpSourceSettings(mergedConfig.as[Int]("max-connections"),
                                        mergedConfig.as[Int]("pre-connection-parallelism"),
                                        mergedConfig.as[Int]("commit-parallelism"),
                                        mergedConfig.as[DataFormat]("format"))

      val rootConfig = system.settings.config
      val serverSettings: ServerSettings =
        if (mergedConfig.hasPath("akka.http.server")) {
          ServerSettings(mergedConfig.withFallback(rootConfig))
        } else {
          ServerSettings(rootConfig)
        }
      val interface = mergedConfig.as[String]("interface")
      val port      = mergedConfig.as[Int]("port")

      logger.info(s"Creating a new HttpSource(${interface}:${port}) with settings ${settings}")

      val httpBind = Http()
        .bind(interface = interface, port = port, settings = serverSettings)
        .map(_.flow)

      new HttpSource(settings, httpBind)

    } catch {
      case ex: Throwable =>
        logger.error(s"Creating a HttpSource failed with error: ${ex.getMessage}")
        throw ex
    }
  }
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
final class HttpRequestHandler()(implicit executor: ExecutionContext, extractor: IExtractor)
    extends GraphStage[FanOutShape2[HttpRequest, Future[HttpResponse], Event]] {

  val unmarshaller: Unmarshaller[HttpEntity, ExtractedData] =
    Unmarshaller.byteStringUnmarshaller.andThen(
      Unmarshaller.apply(_ => data => extractor.extract(data))
    )

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

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          log.debug("onPush in -> push out0")
          push(out0, requestPreprocess(grab(in)))
        }
      })

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
        val responsePromise = Promise[HttpResponse]
        val extractedDataFuture =
          unmarshaller.apply(request.entity)(executor, materializer)
        val callback = getEventExtractingCallback(responsePromise)
        extractedDataFuture.onComplete(result => callback.invoke(result))

        responsePromise.future
      }

      def getEventExtractingCallback(
          responsePromise: Promise[HttpResponse]
      ): AsyncCallback[Try[ExtractedData]] =
        getAsyncCallback[Try[ExtractedData]] {
          case Success(extractedData) =>
            val event = Event(extractedData.metadata, extractedData.body).withCommitter(() => {
              responsePromise.success(successfulResponse).future
            })

            log.debug("push out1")
            push(out1, event)

          case Failure(ex) =>
            log.info(s"Request send failed with error: ${ex.getMessage}")
            responsePromise.success(failedResponse)
            tryPullIn()
        }
    }
}
