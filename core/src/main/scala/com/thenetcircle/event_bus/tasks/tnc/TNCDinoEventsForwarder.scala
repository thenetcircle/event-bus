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

package com.thenetcircle.event_bus.tasks.tnc

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.interfaces.EventStatus.Norm
import com.thenetcircle.event_bus.interfaces.{
  Event,
  EventStatus,
  TransformTask,
  TransformTaskBuilder
}
import com.typesafe.scalalogging.StrictLogging

import scala.util.matching.Regex

class TNCDinoEventsForwarder() extends TransformTask with StrictLogging {

  def appendTitleField(event: Event): Event = {
    if (event.metadata.verb.isDefined && event.metadata.name.isEmpty) {
      val newTitle = "dino." + event.metadata.verb.get
      val newBody = event.body.data.replaceFirst(Regex.quote("{"), s"""{"title": "$newTitle",""")
      event.withName(newTitle).withBody(newBody).withNoGroup()
    } else {
      event
    }
  }

  override def prepare()(
      implicit runningContext: TaskRunningContext
  ): Flow[Event, (EventStatus, Event), NotUsed] = {
    Flow[Event].map(e => Norm -> appendTitleField(e))
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {}

}

class TNCDinoEventsForwarderBuilder() extends TransformTaskBuilder {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): TNCDinoEventsForwarder = {
    new TNCDinoEventsForwarder()
  }

}
