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

package com.thenetcircle.event_bus

import akka.http.scaladsl.Http
import com.thenetcircle.event_bus.dispatcher.{Dispatcher, DispatcherSettings}
import com.thenetcircle.event_bus.pipeline.PipelinePool
import com.thenetcircle.event_bus.tracing.Tracer
import com.thenetcircle.event_bus.transporter.{Transporter, TransporterSettings}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import kamon.Kamon
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object EventBus extends App with StrictLogging {

  // Initialization
  logger.info("Application is initializing.")

  Kamon.start()
  protected implicit val system = akka.actor.ActorSystem()

  PipelinePool.init(
    system.settings.config.as[List[Config]]("event-bus.runtime.pipeline-pool"))
  Tracer.init(system)

  logger.info("Application initialization done.")

  sys.addShutdownHook({
    logger.info("Application is shutting down...")

    logger.info("Kamon is shutting down...")
    Kamon.shutdown()

    logger.info("ActorSystem is shutting down...")
    Http()
      .shutdownAllConnectionPools()
      .map(_ => terminateActorSystemAndWait)(ExecutionContext.global)
  })

  protected def terminateActorSystemAndWait = {
    system.terminate()
    Await.result(system.whenTerminated, 60.seconds)
  }

  // Launch transporters
  logger.info(s"Running transporters.")
  val transportersConfig = system.settings.config
    .as[Option[List[Config]]]("event-bus.runtime.transporters")
  transportersConfig.foreach(configList =>
    configList.foreach(c => {
      val transporterSettings = TransporterSettings(c)
      Transporter(transporterSettings).run()
      logger.info(s"Transporter ${transporterSettings.name} launched.")
    }))

  // Launch dispatchers
  logger.info(s"Running dispatchers.")
  val dispatchersConfig = system.settings.config
    .as[Option[List[Config]]]("event-bus.runtime.dispatchers")
  dispatchersConfig.foreach(configList =>
    configList.foreach(c => {
      val dispatcherSettings = DispatcherSettings(c)
      Dispatcher(dispatcherSettings).run()
      logger.info(s"Dispatcher ${dispatcherSettings.name} launched.")
    }))

}
