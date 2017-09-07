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
import akka.http.scaladsl.settings.ServerSettings
import com.thenetcircle.event_bus.EventFormat
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointPriority.EntryPointPriority
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

/** Http EntryPoint Settings */
case class HttpEntryPointSettings(
    name: String,
    priority: EntryPointPriority,
    maxConnections: Int,
    perConnectionParallelism: Int,
    eventFormat: EventFormat,
    serverSettings: ServerSettings,
    interface: String,
    port: Int
) extends EntryPointSettings

object HttpEntryPointSettings {
  def apply(_config: Config)(
      implicit system: ActorSystem): HttpEntryPointSettings = {
    val config: Config =
      _config.withFallback(
        system.settings.config.getConfig("event-bus.http-entrypoint"))

    val rootConfig =
      system.settings.config
    val serverSettings: ServerSettings =
      if (config.hasPath("akka.http.server")) {
        ServerSettings(config.withFallback(rootConfig))
      } else {
        ServerSettings(rootConfig)
      }

    HttpEntryPointSettings(
      config.as[String]("name"),
      config.as[EntryPointPriority]("priority"),
      config.as[Int]("max-connections"),
      config.as[Int]("pre-connection-parallelism"),
      config.as[EventFormat]("event-format"),
      serverSettings,
      config.as[String]("interface"),
      config.as[Int]("port")
    )
  }
}
