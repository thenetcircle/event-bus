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
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.Event
import com.typesafe.config.Config

import scala.collection.immutable

sealed trait EndPointSettings {
  def name: String
}

object EndPointSettings {
  def apply(config: Config): EndPointSettings = ???
}

trait EndPoint {
  val settings: EndPointSettings
  def port: Flow[Event, Event, NotUsed]
}

object EndPoint {
  def apply(settings: EndPointSettings): EndPoint = ???
}

case class HttpEndPointSettings(
    name: String,
    poolSettings: Option[ConnectionPoolSettings],
    method: HttpMethod = HttpMethods.GET,
    uri: Uri = Uri./,
    headers: immutable.Seq[HttpHeader] = Nil,
    protocol: HttpProtocol = HttpProtocols.`HTTP/1.1`
) extends EndPointSettings
