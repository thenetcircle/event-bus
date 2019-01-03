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

package com.thenetcircle.event_bus.story.tasks.kafka.extended

import java.util

import com.thenetcircle.event_bus.event.Event
import org.apache.kafka.common.serialization.Serializer

class EventSerializer extends Serializer[Event] {
  override def serialize(topic: String, data: Event): Array[Byte] =
    data.body.data.getBytes("UTF-8")

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}
  override def close(): Unit                                                 = {}
}
