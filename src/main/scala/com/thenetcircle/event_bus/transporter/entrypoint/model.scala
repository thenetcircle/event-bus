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
import com.thenetcircle.event_bus.event_extractor.{EventExtractor, EventFormat}
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointPriority.EntryPointPriority
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointType.EntryPointType
import com.thenetcircle.event_bus.Event
import com.typesafe.config.{Config, ConfigException}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader

object EntryPointPriority extends Enumeration {
  type EntryPointPriority = Value
  val High   = Value(6, "High")
  val Normal = Value(3, "Normal")
  val Low    = Value(1, "Low")

  def apply(name: String): EntryPointPriority = name.toUpperCase match {
    case "HIGH"   => High
    case "NORMAL" => Normal
    case "LOW"    => Low
  }

  implicit val entryPointPriorityReader: ValueReader[EntryPointPriority] =
    new ValueReader[EntryPointPriority] {
      override def read(config: Config, path: String) =
        EntryPointPriority(config.getString(path))
    }
}

object EntryPointType extends Enumeration {
  type EntryPointType = Value
  val HTTP = Value(1, "HTTP")

  def apply(name: String): EntryPointType = name.toUpperCase match {
    case "HTTP" => HTTP
  }

  implicit val entryPointTypeReader: ValueReader[EntryPointType] =
    new ValueReader[EntryPointType] {
      override def read(config: Config, path: String) =
        apply(config.getString(path))
    }
}

/** Abstraction Api of All EntryPoints */
trait EntryPoint {
  val settings: EntryPointSettings

  // TODO: add switcher as the materialized value
  def stream: Source[Event, _]
}

/** Create a new EntryPoint based on it's settings
  */
object EntryPoint {

  def apply(settings: EntryPointSettings)(
      implicit system: ActorSystem,
      materializer: Materializer,
      eventExtractor: EventExtractor): EntryPoint =
    settings.entryPointType match {
      case EntryPointType.HTTP =>
        HttpEntryPoint(settings.asInstanceOf[HttpEntryPointSettings])
    }

}

trait EntryPointSettings {
  val name: String
  val priority: EntryPointPriority
  val eventFormat: EventFormat
  val entryPointType: EntryPointType
}

object EntryPointSettings {

  /** Returns a [[EntryPointSettings]] from a TypeSafe [[Config]]
    *
    * @throws ConfigException
    *             if config incorrect
    * @throws IllegalArgumentException
    *             if "type" didn't match any predefined types
    */
  def apply(config: Config)(
      implicit system: ActorSystem): EntryPointSettings = {
    var entryPointType = config.as[EntryPointType]("type")

    entryPointType match {
      case EntryPointType.HTTP =>
        HttpEntryPointSettings(config)

      case _ =>
        throw new IllegalArgumentException(
          """EntryPoint "type" is not correct!""")
    }
  }

}
