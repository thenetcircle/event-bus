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

package com.thenetcircle.event_bus.story.tasks
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.story.TaskRunningContext
import com.thenetcircle.event_bus.story.tasks.kafka.extended.KafkaKey

import scala.util.matching.Regex

package object kafka {
  type ProducerKey   = KafkaKey
  type ProducerValue = Event
  type ConsumerKey   = KafkaKey
  type ConsumerValue = Array[Byte]

  def replaceKafkaTopicSubstitutes(topic: String)(
      implicit runningContext: TaskRunningContext
  ): String = {
    val appContext = runningContext.getAppContext()

    topic
      .replaceAll(Regex.quote("""{app_name}"""), appContext.getAppName())
      .replaceAll(
        Regex.quote("""{app_env}"""),
        if (appContext.isDev() || appContext.isProd()) "" else appContext.getAppEnv()
      )
  }
}
