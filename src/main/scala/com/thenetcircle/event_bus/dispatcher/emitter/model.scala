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

package com.thenetcircle.event_bus.dispatcher.emitter

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.dispatcher.emitter.EmitterType.EmitterType
import com.typesafe.config.{Config, ConfigException}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader

object EmitterType extends Enumeration {
  type EmitterType = Value

  val HTTP = Value(1, "HTTP")

  def apply(name: String): EmitterType = name.toUpperCase match {
    case "HTTP" => HTTP
  }

  implicit val emitterTypeReader: ValueReader[EmitterType] =
    new ValueReader[EmitterType] {
      override def read(config: Config, path: String) =
        apply(config.getString(path))
    }
}

trait Emitter {
  val settings: EmitterSettings
  def stream: Flow[Event, Event, NotUsed]
}

object Emitter {
  def apply(settings: EmitterSettings)(implicit system: ActorSystem,
                                       materializer: Materializer): Emitter =
    settings.emitterType match {
      case EmitterType.HTTP =>
        HttpEmitter(settings.asInstanceOf[HttpEmitterSettings])
    }
}

trait EmitterSettings {
  val name: String
  val emitterType: EmitterType
}

object EmitterSettings {

  /** Returns a [[EmitterSettings]] from a TypeSafe [[Config]]
    *
    * @throws ConfigException
    *             if config incorrect
    * @throws IllegalArgumentException
    *             if "type" didn't match any predefined types
    */
  def apply(config: Config)(implicit system: ActorSystem): EmitterSettings = {
    var emitterType = config.as[EmitterType]("type")

    emitterType match {
      case EmitterType.HTTP =>
        HttpEmitterSettings(config)

      case _ =>
        throw new IllegalArgumentException("""Emitter "type" is not correct!""")
    }
  }
}
