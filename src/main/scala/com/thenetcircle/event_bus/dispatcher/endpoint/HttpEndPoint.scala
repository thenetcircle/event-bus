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

package com.thenetcircle.event_bus.dispatcher.endpoint

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.Event

import scala.collection.immutable

// Notice that each new instance will create a new connection pool based on the poolSettings
class HttpEndPoint(val settings: HttpEndPointSettings)(
    implicit val system: ActorSystem)
    extends EndPoint {

  // TODO: check when it creates a new pool
  private val connectionPool = Http().cachedHostConnectionPool[Event](
    settings.host,
    settings.port,
    settings.poolSettings)

  override val port: Flow[Event, Event, NotUsed] =
    Flow[Event]
      .map(event => {
        settings.defaultRequest.withEntity(HttpEntity(event.body.data)) -> event
      })
      .via(connectionPool)

}
