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

package com.thenetcircle.event_bus.transporter.entrypoint
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.thenetcircle.event_bus.{Event, EventFormat}
import com.typesafe.config.{Config, ConfigException}

object EntryPointPriority {
  val High   = 3
  val Normal = 2
  val Low    = 1
}

sealed trait EntryPointSettings {
  def priority: Int
  def eventFormat: EventFormat
}

object EntryPointSettings {

  /** Get EntryPoint Settings From Config
    * And based on their "type" field
    *
    * @throws ConfigException
    *             if config incorrect
    * @throws IllegalArgumentException
    *             if "type" didn't match any predefined types
    */
  def apply(config: Config): EntryPointSettings = {
    var entryPointType = config.getString("type")

    entryPointType.toUpperCase() match {
      case "HTTP" =>
        HttpEntryPointSettings(
          config.getString("name"),
          config.getString("interface"),
          config.getInt("port"),
          if (config.hasPath("priority")) config.getInt("priority")
          else EntryPointPriority.Normal,
          if (config.hasPath("format")) EventFormat(config.getString("format"))
          else EventFormat.DefaultFormat
        )
      case _ =>
        throw new IllegalArgumentException("""EntryPoint "type" is not set!""")
    }
  }

}

/** Http EntryPoint Settings */
case class HttpEntryPointSettings(
    name: String,
    interface: String,
    port: Int,
    priority: Int,
    eventFormat: EventFormat
) extends EntryPointSettings

/** Abstraction Api of All EntryPoints */
trait EntryPoint {
  def port: Source[Source[Event, _], _]
}

/** Create a new EntryPoint based on it's settings
  */
object EntryPoint {

  def apply(settings: EntryPointSettings)(
      implicit system: ActorSystem,
      materializer: Materializer): EntryPoint = settings match {
    case s: HttpEntryPointSettings =>
      HttpEntryPoint(s)
  }

}
