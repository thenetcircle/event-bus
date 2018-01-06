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
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object Runner extends App with StrictLogging {

  // Initialization
  logger.info("Application is initializing.")

  implicit val actorSystem = akka.actor.ActorSystem("event-bus")
  AppContext.init("2.0.0")

  // Kamon.start()

  sys.addShutdownHook({
    logger.info("Application is shutting down...")

    // Kamon.shutdown()

    Http()
      .shutdownAllConnectionPools()
      .map(_ => {
        actorSystem.terminate()
        Await.result(actorSystem.whenTerminated, 60.seconds)
      })(ExecutionContext.global)
  })

}
