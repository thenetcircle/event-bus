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
/*
package com.thenetcircle.event_bus.story

import com.thenetcircle.event_bus.interface.SourceTaskBuilder
import com.thenetcircle.event_bus.misc.ConfigStringParser
import com.typesafe.scalalogging.StrictLogging

/** Builds Story By Config */
class StoryBuilder(builderFactory: TaskBuilderFactory)
    extends SourceTaskBuilder
    with StrictLogging {

  /**
 * Builds Story by String Config
 *
 * example:
 * ```
 * {
 *   # "name": "..."
 *   # "sourceTask": ["sourceTask-type", "settings"]
 *   # "transformTasks": [
 *   #   ["op-type", "settings"],
 *   #   ...
 *   # ]
 *   # "sinkTask": ["sinkTask-type", "settings"]
 *   # "fallbackTasks": [
 *     ["sinkTask-type", "settings"]
 *   ]
 * }
 * ```
 */
  def build(configString: String)(implicit runningContext: TaskRunningContext): Story = {

    try {

      val config = ConfigStringParser.convertStringToConfig(configString)

      val storyName = config.getString("name")
      val storySettings = StorySettings()

      val AConfig = config.as[List[String]]("sourceTask")
      val taskA = builderFactory.buildTaskA(AConfig(0), AConfig(1)).get

      val taskB = config
        .as[Option[List[List[String]]]]("transformTasks")
        .map(_.map {
          case _type :: _settings :: _ => builderFactory.buildTaskB(_type, _settings).get
        })

      val taskC = config.as[Option[List[String]]]("sinkTask").map {
        case _type :: _settings :: _ => builderFactory.buildTaskC(_type, _settings).get
      }

      val fallbacks = config
        .as[Option[List[List[String]]]]("fallbackTasks")
        .map(_.map {
          case _type :: _settings :: _ => builderFactory.buildTaskC(_type, _settings).get
        })

      new Story(storyName, storySettings, taskA, taskB, taskC, fallbacks)

    } catch {

      case ex: Throwable =>
        logger.error(s"Creating Story failed with error: ${ex.getMessage}")
        throw ex

    }

  }

}
 */
