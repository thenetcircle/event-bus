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

import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import com.thenetcircle.event_bus.context.AppContext
import com.typesafe.scalalogging.StrictLogging

class Router()(implicit appContext: AppContext) extends StrictLogging {

  val staticDir: String = appContext.getSystemConfig().getString("app.admin.static_dir")

  logger.debug(s"static directory $staticDir")

  import StoryInfoJsonSupport._

  def getRoute(actionHandler: ActionHandler): Route =
    // format: off
    pathPrefix("api") {
      path("internal" / "zktree") {
        get {
          parameter("path".?) { path =>
            complete(actionHandler.getZKTree(path))
          }
        } ~
        post {
          parameter("path".?) { path =>
            entity(as[String]) { json =>
              complete(actionHandler.updateZKTree(path, json))
            }
          }
        }
      } ~
      get {
        path("stories") {
          complete(actionHandler.getStories())
        } ~
        path("story" / Segment) { storyName =>
          complete(actionHandler.getStory(storyName))
        } ~
        path("runners") {
          complete(actionHandler.getRunners())
        } ~
        path("runner" / Segment) { runnerName =>
          complete(actionHandler.getRunner(runnerName))
        } ~
        path("topics") {
          complete(actionHandler.getTopics())
        }
      } ~
      post {
        path("story" / "create") {
          entity(as[StoryInfo]) { storyInfo =>
            complete(actionHandler.createStory(storyInfo))
          }
        } ~
        path("story" / "update") {
          entity(as[StoryInfo]) { storyInfo =>
            complete(actionHandler.updateStory(storyInfo))
          }
        } ~
        path("runner" / "assign") {
          parameters('runnerName, 'storyName) { (runnerName, storyName) =>
            complete(actionHandler.assignStory(runnerName, storyName))
          }
        } ~
        path("runner" / "unassign") {
          parameters('runnerName, 'storyName) { (runnerName, storyName) =>
            complete(actionHandler.unassignStory(runnerName, storyName))
          }
        } ~
        path("topics") {
          entity(as[String]) { topics =>
            complete(actionHandler.updateTopics(topics))
          }
        }
      }
    } ~
    getFromDirectory(staticDir) ~
    get {
      getFromFile(s"$staticDir/index.html")
    }
    // format: on
}
