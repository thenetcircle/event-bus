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
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry

import scala.concurrent.Await
import scala.concurrent.duration._

object Runner extends App with StrictLogging {

  // Initialization
  logger.info("Application is initializing.")

  implicit val environment: Environment = Environment(ConfigFactory.load())
  implicit val system: ActorSystem =
    ActorSystem(environment.getAppName(), environment.getConfig())

  val zkclient: CuratorFramework =
    CuratorFrameworkFactory.newClient("", new ExponentialBackoffRetry(1000, 3))
  zkclient.start()

  // Kamon.start()
  sys.addShutdownHook({
    logger.info("Application is shutting down...")

    // Kamon.shutdown()

    zkclient.close()

    environment.shutdown()

    system.terminate()
    Await.result(system.whenTerminated, 60.seconds)

    /*Http()
      .shutdownAllConnectionPools()
      .map(_ => {
        globalActorSystem.terminate()
        Await.result(globalActorSystem.whenTerminated, 60.seconds)
      })(ExecutionContext.global)*/
  })

}