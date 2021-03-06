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

import akka.actor.ActorSystem
import com.thenetcircle.event_bus.misc.Logging
import com.typesafe.config.Config

import scala.concurrent.Await
import scala.concurrent.duration._

trait AbstractApp extends Logging {

  logger.info("Application is initializing.")

  def config: Config

  implicit lazy val appContext: AppContext = AppContext(config)
  implicit lazy val system: ActorSystem    = ActorSystem(appContext.getAppName(), config)

  // Setup shutdown hooks
  sys.addShutdownHook({
    logger.info("Application is shutting down...")
    appContext.shutdown()
    system.terminate()
    Await.result(system.whenTerminated, 6.seconds)
  })

}
