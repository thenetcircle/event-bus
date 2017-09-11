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
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.dispatcher.endpoint.EndPointType.EndPointType
import com.typesafe.config.{Config, ConfigException}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader

object EndPointType extends Enumeration {
  type EndPointType = Value

  val HTTP = Value(1, "HTTP")

  def apply(name: String): EndPointType = name.toUpperCase match {
    case "HTTP" => HTTP
  }

  implicit val entryPointTypeReader: ValueReader[EndPointType] =
    new ValueReader[EndPointType] {
      override def read(config: Config, path: String) =
        apply(config.getString(path))
    }
}

trait EndPoint {
  val settings: EndPointSettings
  def stream: Flow[Event, Event, NotUsed]
}

object EndPoint {
  def apply(settings: EndPointSettings)(implicit system: ActorSystem,
                                        materializer: Materializer): EndPoint =
    settings.endPointType match {
      case EndPointType.HTTP =>
        HttpEndPoint(settings.asInstanceOf[HttpEndPointSettings])
    }
}

trait EndPointSettings {
  val name: String
  val endPointType: EndPointType
}

object EndPointSettings {

  /** Returns a [[EndPointSettings]] from a TypeSafe [[Config]]
    *
    * @throws ConfigException
    *             if config incorrect
    * @throws IllegalArgumentException
    *             if "type" didn't match any predefined types
    */
  def apply(config: Config)(implicit system: ActorSystem): EndPointSettings = {
    var endPointType = config.as[EndPointType]("type")

    endPointType match {
      case EndPointType.HTTP =>
        HttpEndPointSettings(config)

      case _ =>
        throw new IllegalArgumentException(
          """EndPoint "type" is not correct!""")
    }
  }
}
