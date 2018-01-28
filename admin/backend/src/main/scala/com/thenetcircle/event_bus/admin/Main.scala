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

package com.thenetcircle.event_bus.admin

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}
import com.thenetcircle.event_bus.Core
import com.thenetcircle.event_bus.misc.ZooKeeperManager
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Await
import scala.concurrent.duration._

class Main extends Core {

  val config: Config = ConfigFactory.load()

  def run(args: Array[String]): Unit = {

    implicit val materializer: Materializer = ActorMaterializer()
    import scala.concurrent.ExecutionContext.Implicits.global

    val zkManager: ZooKeeperManager = ZooKeeperManager.createInstance(appendEnv = false)

    val actionHandler = new ActionHandler(zkManager)
    val route: Route  = new Router().getRoute(actionHandler)

    val interface     = config.getString("admin.interface")
    val port          = config.getInt("admin.port")
    val bindingFuture = Http().bindAndHandle(route, interface, port)

    appContext.addShutdownHook(Await.result(bindingFuture.map(_.unbind()), 5.seconds))

  }

}

object Main extends App { (new Main).run(args) }
