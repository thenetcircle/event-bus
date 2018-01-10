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

package com.thenetcircle.event_bus.story

import com.thenetcircle.event_bus.RunningContext
import com.thenetcircle.event_bus.factory.PlotBuilderFactory
import com.thenetcircle.event_bus.interface.{IOp, ISourceBuilder}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

/** Builds Story By Config */
class StoryBuilder() extends ISourceBuilder with StrictLogging {

  /**
   * Builds Story by String Config
   *
   * example:
   * ```
   * {
   *   # "name": "..."
   *   # "source": { "type": "...", "settings": {} }
   *   # "ops": [ { "type": "", "settings": {} } ]
   *   # "sink": { "type": "...", "settings": {} }
   *   # "fallback": {}
   * }
   * ```
   */
  override def build(configString: String)(implicit runningContext: RunningContext): Story = {

    try {

      val config = convertStringToConfig(configString)

      val storySettings = StorySettings(config.getString("name"))

      val source = PlotBuilderFactory
        .buildSource(config.getString("source.type"), config.getString("source.settings"))
        .get

      val sink = PlotBuilderFactory
        .buildSink(config.getString("sink.type"), config.getString("sink.settings"))
        .get

      val ops = config
        .as[Option[List[Config]]]("ops")
        .map(_.map(_config => {
          PlotBuilderFactory
            .buildOp(_config.getString("type"), _config.getString("settings"))
            .get
        }))
        .getOrElse(List.empty[IOp])

      val fallback = config
        .as[Option[Config]]("fallback")
        .map(
          _config =>
            PlotBuilderFactory
              .buildSink(_config.getString("type"), _config.getString("settings"))
              .get
        )

      new Story(storySettings, source, sink, ops, fallback)

    } catch {

      case ex: Throwable =>
        logger.error(s"Creating Story failed with error: ${ex.getMessage}")
        throw ex

    }

  }

}
