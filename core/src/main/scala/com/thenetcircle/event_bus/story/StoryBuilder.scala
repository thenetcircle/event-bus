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

import com.thenetcircle.event_bus.interface.TaskABuilder
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

/** Builds Story By Config */
class StoryBuilder() extends TaskABuilder with StrictLogging {

  /**
   * Builds Story by String Config
   *
   * example:
   * ```
   * {
   *   # "name": "..."
   *   # "taskA": ["taskA-type", "settings"]
   *   # "taskB": [
   *   #   ["op-type", "settings"],
   *   #   ...
   *   # ]
   *   # "taskC": ["taskC-type", "settings"]
   *   # "alternativeC": [
   *     ["taskC-type", "settings"]
   *   ]
   * }
   * ```
   */
  def build(configString: String)(implicit context: TaskExecutingContext): Story = {

    try {

      val builderFactory = context.getBuilderFactory()
      val config = convertStringToConfig(configString)

      val storySettings = StorySettings(config.getString("name"))

      val AConfig = config.as[List[String]]("taskA")
      val taskA = builderFactory.buildTaskA(AConfig(0), AConfig(1)).get

      val taskB = config
        .as[Option[List[List[String]]]]("taskB")
        .map(_.map {
          case _type :: _settings :: _ => builderFactory.buildTaskB(_type, _settings).get
        })

      val taskC = config.as[Option[List[String]]]("taskC").map {
        case _type :: _settings :: _ => builderFactory.buildTaskC(_type, _settings).get
      }

      val alternativeC = config
        .as[Option[List[List[String]]]]("alternativeC")
        .map(_.map {
          case _type :: _settings :: _ => builderFactory.buildTaskC(_type, _settings).get
        })

      new Story(storySettings, taskA, taskB, taskC, alternativeC)

    } catch {

      case ex: Throwable =>
        logger.error(s"Creating Story failed with error: ${ex.getMessage}")
        throw ex

    }

  }
}
