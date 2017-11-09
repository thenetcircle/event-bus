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

package com.thenetcircle.event_bus.transporter.receiver

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.thenetcircle.event_bus.event_extractor.{EventExtractor, EventFormat}
import com.thenetcircle.event_bus.transporter.receiver.ReceiverPriority.ReceiverPriority
import com.thenetcircle.event_bus.transporter.receiver.ReceiverType.ReceiverType
import com.thenetcircle.event_bus.Event
import com.typesafe.config.{Config, ConfigException}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader

object ReceiverPriority extends Enumeration {
  type ReceiverPriority = Value

  val High = Value(6, "High")
  val Normal = Value(3, "Normal")
  val Low = Value(1, "Low")

  def apply(name: String): ReceiverPriority = name.toUpperCase match {
    case "HIGH"   => High
    case "NORMAL" => Normal
    case "LOW"    => Low
  }

  implicit val receiverPriorityReader: ValueReader[ReceiverPriority] =
    new ValueReader[ReceiverPriority] {
      override def read(config: Config, path: String) =
        ReceiverPriority(config.getString(path))
    }
}

object ReceiverType extends Enumeration {
  type ReceiverType = Value

  val HTTP = Value(1, "HTTP")

  def apply(name: String): ReceiverType = name.toUpperCase match {
    case "HTTP" => HTTP
  }

  implicit val receiverTypeReader: ValueReader[ReceiverType] =
    new ValueReader[ReceiverType] {
      override def read(config: Config, path: String) =
        apply(config.getString(path))
    }
}

/** Abstraction Api of All Receivers */
trait Receiver {
  val settings: ReceiverSettings

  // TODO: add switcher as the materialized value
  def stream: Source[Event, _]
}

/** Create a new Receiver based on it's settings
  */
object Receiver {

  def apply(settings: ReceiverSettings)(implicit system: ActorSystem,
                                        materializer: Materializer,
                                        eventExtractor: EventExtractor): Receiver =
    settings.receiverType match {
      case ReceiverType.HTTP =>
        HttpReceiver(settings.asInstanceOf[HttpReceiverSettings])
    }

}

trait ReceiverSettings {
  val name: String
  val priority: ReceiverPriority
  val eventFormat: EventFormat
  val receiverType: ReceiverType
}

object ReceiverSettings {

  /** Returns a [[ReceiverSettings]] from a TypeSafe [[Config]]
    *
    * @throws ConfigException
    *             if config incorrect
    * @throws IllegalArgumentException
    *             if "type" didn't match any predefined types
    */
  def apply(config: Config)(implicit system: ActorSystem): ReceiverSettings = {
    val receiverType = config.as[ReceiverType]("type")

    receiverType match {
      case ReceiverType.HTTP =>
        HttpReceiverSettings(config)

      case _ =>
        throw new IllegalArgumentException("""Receiver "type" is not correct!""")
    }
  }

}
