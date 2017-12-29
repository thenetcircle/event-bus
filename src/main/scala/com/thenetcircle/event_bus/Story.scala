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

package com.thenetcircle.event_bus

import com.thenetcircle.event_bus.StoryStatus.StoryStatus
import com.thenetcircle.event_bus.interface._
import com.typesafe.config.Config

class Story(settings: StorySettings,
            source: ISource,
            sink: ISink,
            opsList: List[IOperation] = List.empty,
            initStatus: StoryStatus = StoryStatus.INIT) {

  private var status: StoryStatus = initStatus

  def updateStatus(_status: StoryStatus): Unit = {
    status = _status
  }

  def run(): Unit = ???

}

object Story {

  def apply(config: Config): Story = ???

}

case class StorySettings(parallelism: Int)

object StoryStatus extends Enumeration {
  type StoryStatus = Value

  val INIT = Value(1, "INIT")
  val DEPLOYING = Value(2, "DEPLOYING")
  val RUNNING = Value(3, "RUNNING")
  val FAILED = Value(4, "FAILED")
  val STOPPING = Value(5, "STOPPING")
  val STOPPED = Value(6, "STOPPED")
}
