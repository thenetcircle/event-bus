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
  def apply(config: Config)(
      implicit system: ActorSystem): HttpEntryPointSettings = {

    // TODO: custom client settings with system fallbacker?

    val priority =
      if (config.hasPath("priority"))
        EntryPointPriority(config.getInt("priority"))
      else EntryPointPriority.Normal

    // TODO: adjust these default values when doing stress testing
    // TODO: use reference.conf to set up default value
    val maxConnections =
      if (config.hasPath("max-connections"))
        config.getInt("max-connections")
      else 1000

    val perConnectionParallelism =
      if (config.hasPath("pre-connection-parallelism"))
        config.getInt("pre-connection-parallelism")
      else 10

    val eventFormat =
      if (config.hasPath("format"))
        EventFormat(config.getString("format"))
      else EventFormat.DefaultFormat

    val systemServerSettings =
      system.settings.config.getConfig("akka.server")
    val serverSettings: ServerSettings =
      if (config.hasPath("server")) {
        ServerSettings(
          config
            .getConfig("server")
            .withFallback(systemServerSettings))
      } else {
        ServerSettings(systemServerSettings)
      }

    HttpEntryPointSettings(
      config.getString("name"),
      priority,
      maxConnections,
      perConnectionParallelism,
      eventFormat,
      serverSettings,
      config.getString("interface"),
      config.getInt("port")
    )
  }
}
