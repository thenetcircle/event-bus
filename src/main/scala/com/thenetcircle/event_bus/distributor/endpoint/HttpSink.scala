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

package com.thenetcircle.event_bus.distributor.endpoint

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings

import scala.collection.immutable

case class HttpSinkSettings(
    host: String,
    port: Int = 80,
    poolSettings: Option[ConnectionPoolSettings],
    method: HttpMethod = HttpMethods.GET,
    uri: Uri = Uri./,
    headers: immutable.Seq[HttpHeader] = Nil,
    entity: RequestEntity = HttpEntity.Empty,
    protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`
) extends EndPointSettings

// Notice that each new instance will create a new connection pool based on the poolSettings
class HttpSink(settings: HttpSinkSettings)(implicit val system: ActorSystem) extends EndPoint {

  /*private val connectionPool = Http().cachedHostConnectionPool[Event](
    settings.host,
    settings.port,
    settings.poolSettings.getOrElse(ConnectionPoolSettings(system))
  )

  def inlet(): Flow[Event, Event, NotUsed] =
    Flow[Event]
      .map(HttpRequest())*/

}