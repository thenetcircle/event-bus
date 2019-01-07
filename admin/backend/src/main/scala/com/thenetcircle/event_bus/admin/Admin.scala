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
import com.thenetcircle.event_bus.{AbstractApp, AppContext}
import com.thenetcircle.event_bus.misc.ZKManager
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Await
import scala.concurrent.duration._

class Admin extends AbstractApp {

  val config: Config = ConfigFactory.load()

  def run(args: Array[String]): Unit = {
    lazy implicit val materializer: Materializer = ActorMaterializer()
    import scala.concurrent.ExecutionContext.Implicits.global

    val actionHandler = new ActionHandler(initZKManager())
    val route: Route  = new Router().getRoute(actionHandler)

    val interface     = config.getString("app.admin.interface")
    val port          = config.getInt("app.admin.port")
    val bindingFuture = Http().bindAndHandle(route, interface, port)

    appContext.addShutdownHook(Await.result(bindingFuture.map(_.unbind()), 5.seconds))
  }

  private def initZKManager()(implicit appContext: AppContext): ZKManager = {
    val config        = appContext.getSystemConfig()
    val connectString = config.getString("app.zookeeper.servers")
    val rootPath      = appContext.getSystemConfig().getString("app.zookeeper.rootpath") + s"/${appContext.getAppName()}"
    val zkManager     = new ZKManager(connectString, rootPath)
    appContext.addShutdownHook(zkManager.close())
    appContext.setZKManager(zkManager)
    zkManager
  }

}

object Admin extends App { (new Admin).run(args) }
