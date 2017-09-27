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

package com.thenetcircle.event_bus.dispatcher

import akka.actor.ActorSystem
import akka.stream.scaladsl.RunnableGraph
import akka.stream.{ActorMaterializer, Materializer}
import com.thenetcircle.event_bus.dispatcher.endpoint.EndPoint
import com.thenetcircle.event_bus.pipeline.PipelineOutlet
import com.typesafe.scalalogging.StrictLogging

import scala.util.control.NonFatal

class Dispatcher(
    settings: DispatcherSettings,
    pipelineOutlet: PipelineOutlet,
    endPoints: Vector[EndPoint])(implicit materializer: Materializer)
    extends StrictLogging {

  logger.debug(
    s"new Dispatcher ${settings.name} is created with settings: $settings")

  def getNextEndpoint(index: Int): EndPoint =
    if (endPoints.size == 1)
      endPoints(0)
    else
      endPoints(index % endPoints.size)

  private var sourceIndex = 0

  // TODO: draw a graph in comments
  // TODO: error handler
  // TODO: parallel and async
  // TODO: Mat value
  lazy val dispatchStream: RunnableGraph[_] =
    pipelineOutlet.stream
      .flatMapMerge(
        1000,
        source => {
          logger.info(s"new source is coming")
          var retryIndex = sourceIndex

          val result = source
            .via(getNextEndpoint(sourceIndex).stream)
            .recoverWithRetries(attempts = endPoints.size - 1, {
              case NonFatal(ex) =>
                retryIndex += 1
                source.via(getNextEndpoint(retryIndex).stream)
            })

          sourceIndex += 1
          result
        }
      )
      .to(pipelineOutlet.committer.async)

  // TODO add a transporter controller as a materialized value
  def run(): Unit = {
    logger.info(s"Dispatcher ${settings.name} is going to run")
    dispatchStream.run()
  }

}

object Dispatcher {
  def apply(settings: DispatcherSettings)(
      implicit system: ActorSystem): Dispatcher = {

    implicit val materializer =
      ActorMaterializer(settings.materializerSettings, Some(settings.name))

    val pipelineOutlet =
      settings.pipeline.getNewOutlet(settings.pipelineOutletSettings)

    val endPoints = settings.endPointSettings.map(EndPoint(_))

    new Dispatcher(settings, pipelineOutlet, endPoints)
  }
}
