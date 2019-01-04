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

package com.thenetcircle.event_bus.story.tasks.tnc

import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.EventStatus.NORM
import com.thenetcircle.event_bus.misc.Logging
import com.thenetcircle.event_bus.story.interfaces.{ITransformTask, ITransformTaskBuilder}
import com.thenetcircle.event_bus.story.{Payload, StoryMat}

import scala.util.matching.Regex

class TNCDinoEventsForwarder() extends ITransformTask with Logging {

  def appendTitleField(event: Event): Event = {
    val verbOption = event.getExtra("verb")
    if (verbOption.isDefined) {
      val shortGroup = event.metadata.topic.map(g => g.split("-").last + ".").getOrElse("wio.")
      val newTitle   = "dino." + shortGroup + verbOption.get
      val newBody    = event.body.data.replaceFirst(Regex.quote("{"), s"""{"title": "$newTitle",""")

      consumerLogger.debug(s"appending new group: $shortGroup, new title: $newTitle to the event ${event.uuid}")
      event.withNoTopic().withName(newTitle).withBody(newBody)
    } else {
      event
    }
  }

  override def flow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat] =
    Flow[Payload].map {
      case (NORM, event) => NORM -> appendTitleField(event)
      case others        => others
    }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {}

}

class TNCDinoEventsForwarderBuilder() extends ITransformTaskBuilder {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): TNCDinoEventsForwarder =
    new TNCDinoEventsForwarder()

}
