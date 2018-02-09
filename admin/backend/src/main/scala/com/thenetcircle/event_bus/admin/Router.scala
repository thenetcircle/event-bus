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
import com.thenetcircle.event_bus.context.AppContext
import com.typesafe.scalalogging.StrictLogging

class Router()(implicit appContext: AppContext) extends StrictLogging {

  val staticDir = appContext.getSystemConfig().getString("app.admin.static_dir")

  logger.debug(s"static directory $staticDir")

  def createResponse(code: Int, errorMessage: String = ""): String = {
    val message = errorMessage.replaceAll("""\\""", """\\\\""").replaceAll("\"", "\\\\\"")
    s"""{"code": "$code", "message": "$message"}"""
  }

  def wrapPath(path: Option[String]): String =
    if (path.isEmpty || path.get.isEmpty)
      appContext.getAppEnv()
    else
      s"${appContext.getAppEnv()}/${path.get}"

  def getRoute(actionHandler: ActionHandler): Route =
    // format: off
    pathPrefix("api") {
      path("internal" / "zktree") {
        get {
          parameter("path".?) { path =>
            complete(actionHandler.getZKNodeTreeAsJson(wrapPath(path)))
          }
        } ~
        post {
          parameter("path".?) { path =>
            entity(as[String]) { json =>
              try {
                actionHandler.updateZKNodeTreeByJson(wrapPath(path), json)
                complete(createResponse(0))
              } catch {
                case ex: Throwable => complete(createResponse(1, ex.getMessage))
              }
            }
          }
        }
      } ~
      path("stories") {
        complete(actionHandler.getZKNodeTreeAsJson(wrapPath(Some("stories"))))
      }
    } ~
    getFromDirectory(staticDir) ~
    pathEndOrSingleSlash {
      getFromFile(s"$staticDir/index.html")
    }

    // format: on
}
