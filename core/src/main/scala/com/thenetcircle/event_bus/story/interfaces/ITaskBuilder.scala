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

package com.thenetcircle.event_bus.story.interfaces

import com.thenetcircle.event_bus.context.AppContext
import com.typesafe.config.Config

trait ITaskBuilder[+T <: ITask] {

  /**
    * Using in configuration key
    * @return String
    */
  def taskType: String

  /**
    * The default Config will be a fallback of user defined Config
    * @return
    */
  def defaultConfig: Config

  /**
    * Build task based on Config
    * @param config combined Config (user defined and default)
    * @param appContext context
    * @return [[ITask]]
    */
  def buildTask(config: Config)(implicit appContext: AppContext): T

}
