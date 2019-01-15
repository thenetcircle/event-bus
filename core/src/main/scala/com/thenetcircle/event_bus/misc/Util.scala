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

package com.thenetcircle.event_bus.misc

import com.thenetcircle.event_bus.event.Event
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigSyntax}

object Util {

  private lazy val jsonStringParseOptions = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON)

  def convertJsonStringToConfig(configString: String): Config =
    ConfigFactory.parseString(configString.replaceAll("""^\s*\#.*""", ""), jsonStringParseOptions)

  def getLastPartOfPath(path: String): String =
    try {
      path.substring(path.lastIndexOf('/') + 1)
    } catch {
      case _: Throwable => ""
    }

  def makeUTF8String(bytes: Array[Byte]): String = new String(bytes, "UTF-8")

  def getBriefOfEvent(event: Event): String =
    s"uuid: ${event.uuid}, name: ${event.metadata.name}, channel: ${event.metadata.channel},  createdAt: ${event.createdAt}"

}
