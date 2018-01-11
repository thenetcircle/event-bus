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

import com.thenetcircle.event_bus.interface.ISourceBuilder
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
   *   # "source": ["source-type", "settings"]
   *   # "ops": [
   *   #   ["op-type", "settings"],
   *   #   ...
   *   # ]
   *   # "sink": ["sink-type", "settings"]
   *   # "fallback": ["sink-type", "settings"]
   * }
   * ```
   */
  def build(configString: String)(implicit context: StoryExecutingContext): Story = {

    try {

      val builderFactory = context.getBuilderFactory()
      val config = convertStringToConfig(configString)

      val storySettings = StorySettings(config.getString("name"))

      val sourceSettings = config.as[List[String]]("source")
      val source = builderFactory.buildSource(sourceSettings(0), sourceSettings(1)).get

      val ops = config
        .as[Option[List[List[String]]]]("ops")
        .map(_.map(_list => {
          builderFactory
            .buildOp(_list(0), _list(1))
            .get
        }))

      val sink = config.as[Option[List[String]]]("sink").map {
        case _type :: _settings :: _ => builderFactory.buildSink(_type, _settings).get
      }

      val fallback = config.as[Option[List[String]]]("fallback").map {
        case _type :: _settings :: _ => builderFactory.buildSink(_type, _settings).get
      }

      new Story(storySettings, source, ops, sink, fallback)

    } catch {

      case ex: Throwable =>
        logger.error(s"Creating Story failed with error: ${ex.getMessage}")
        throw ex

    }

  }
}
