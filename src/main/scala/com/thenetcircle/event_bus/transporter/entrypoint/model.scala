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
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.event_extractor.EventExtractor
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointPriority.EntryPointPriority
import com.typesafe.config.{Config, ConfigException}

sealed trait EntryPointSettings {
  def name: String
  def priority: EntryPointPriority
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
        val priority =
          if (config.hasPath("priority"))
            EntryPointPriority(config.getInt("priority"))
          else EntryPointPriority.Normal

        HttpEntryPointSettings(
          config.getString("name"),
          priority,
          config.getString("interface"),
          config.getInt("port")
        )
      case _ =>
        throw new IllegalArgumentException("""EntryPoint "type" is not set!""")
    }
  }

}

/** Http EntryPoint Settings */
case class HttpEntryPointSettings(
    name: String,
    priority: EntryPointPriority,
    interface: String,
    port: Int
) extends EntryPointSettings

/** Abstraction Api of All EntryPoints */
trait EntryPoint {
  def port: Source[Source[Event, NotUsed], _]
}

/** Create a new EntryPoint based on it's settings
  */
object EntryPoint {

  def apply(settings: EntryPointSettings)(
      implicit system: ActorSystem,
      materializer: Materializer,
      eventExtractor: EventExtractor): EntryPoint = settings match {
    case s: HttpEntryPointSettings =>
      HttpEntryPoint(s)
  }

}

object EntryPointPriority extends Enumeration {
  type EntryPointPriority = Value
  val High   = Value(3, "High")
  val Normal = Value(2, "Normal")
  val Low    = Value(1, "Low")
}
