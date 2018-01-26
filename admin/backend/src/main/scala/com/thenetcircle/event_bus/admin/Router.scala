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

import akka.http.scaladsl.server.Directives.{complete, get, path, pathSingleSlash, _}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging

class Router extends StrictLogging {

  def getRoute(actionHandler: ActionHandler): Route =
    pathSingleSlash {
      // homepage
      complete("event-bus admin is running!")
    } ~
      path("zk" / "tree") {
        get {
          parameter("path") { path =>
            complete(actionHandler.getZKNodeTreeAsJson(path))
          }
        } ~
          post {
            parameter("path") { path =>
              entity(as[String]) { json =>
                try {
                  complete(actionHandler.updateZKNodeTreeByJson(path, json))
                } catch {
                  case ex: Throwable => complete(ex)
                }
              }
            }
          }
      }

}