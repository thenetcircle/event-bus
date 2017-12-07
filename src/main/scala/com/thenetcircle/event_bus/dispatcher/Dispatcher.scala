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
import akka.stream.scaladsl.{RestartSource, RunnableGraph}
import akka.stream.{ActorMaterializer, Materializer}
import com.thenetcircle.event_bus.dispatcher.emitter.Emitter
import com.thenetcircle.event_bus.pipeline.model.PipelineOutlet
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.util.control.NonFatal

class Dispatcher(settings: DispatcherSettings,
                 pipelineOutlet: PipelineOutlet,
                 emitter: Vector[Emitter])(implicit materializer: Materializer)
    extends StrictLogging {

  logger.info(s"new Dispatcher ${settings.name} is created")

  def getNextEmitter(index: Int): Emitter =
    if (emitter.size == 1)
      emitter(0)
    else
      emitter(index % emitter.size)

  private var sourceIndex = 0
  private val dataSource =
    RestartSource.withBackoff(minBackoff = 1.second, maxBackoff = 1.minute, randomFactor = 0.2) {
      () =>
        logger.info(s"Creating a outlet of pipeline ${pipelineOutlet.pipeline.settings.name}")
        try {
          pipelineOutlet.stream()
        } catch {
          case ex: Throwable =>
            logger.error(s"Create new PipelineOutlet failed with error: ${ex.getMessage}")
            throw ex
        }
    }

  // TODO: draw a graph in comments
  // TODO: error handler
  // TODO: parallel and async
  // TODO: Mat value
  lazy val dispatchStream: RunnableGraph[_] =
    dataSource
      .flatMapMerge(
        1000,
        source => {
          logger.info(s"new source is coming")
          var retryIndex = sourceIndex

          val result = source
            .via(getNextEmitter(sourceIndex).stream)
            .recoverWithRetries(attempts = emitter.size - 1, {
              case NonFatal(ex) =>
                retryIndex += 1
                source.via(getNextEmitter(retryIndex).stream)
            })

          sourceIndex += 1
          result
        }
      )
      .to(pipelineOutlet.ackStream().async)

  // TODO add a transporter controller as a materialized value
  def run(): Unit = {
    logger.info(s"Dispatcher ${settings.name} is going to run")
    dispatchStream.run()
  }

}

object Dispatcher extends StrictLogging {
  def apply(settings: DispatcherSettings)(implicit system: ActorSystem): Dispatcher = {

    logger.info(
      s"Creating a new Dispatcher ${settings.name} from the DispatcherSettings: $settings"
    )

    implicit val materializer: Materializer =
      ActorMaterializer(settings.materializerSettings, Some(settings.name))

    val emitters = settings.emitterSettings.map(Emitter(_))

    new Dispatcher(settings, settings.pipelineOutlet, emitters)
  }
}
