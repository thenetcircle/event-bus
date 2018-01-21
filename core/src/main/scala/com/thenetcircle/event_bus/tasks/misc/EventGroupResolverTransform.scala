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

package com.thenetcircle.event_bus.tasks.misc

import java.util.concurrent.ConcurrentHashMap

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.helper.ConfigStringParser
import com.thenetcircle.event_bus.interfaces.EventStatus.{Fail, Norm}
import com.thenetcircle.event_bus.interfaces.{TransformTask, TransformTaskBuilder}
import com.thenetcircle.event_bus.interfaces.Event
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

class EventGroupResolverTransform(defaultGroup: String, useCache: Boolean = false)
    extends TransformTask
    with StrictLogging {

  private var inited: Boolean = false
  private var index: Map[String, String] = Map.empty
  private val cached: ConcurrentHashMap[String, String] = new ConcurrentHashMap()

  def init(): Unit = if (!inited) {
    // TODO: get data from zookeeper
    /*updateMapping(
      Map(
        "event-user" -> Map("patterns" -> s"""user.*|||profile.*"""),
        "event-message" -> Map("patterns" -> """message.*""")
      )
    )*/
    inited = true
  }

  def getIndex(): Map[String, String] = synchronized { index }
  def updateIndex(_index: Map[String, String]): Unit = synchronized { index = _index }
  def updateMapping(_mapping: Map[String, Map[String, String]]): Unit = {
    val _index = mutable.Map.empty[String, String]
    _mapping.foreach {
      case (group, submap) =>
        submap
          .get("patterns")
          .foreach(_.split(Regex.quote(ConfigStringParser.delimiter)).foreach(pattern => {
            _index += (pattern -> group)
          }))
    }
    updateIndex(_index.toMap)
    if (useCache) cached.clear()
  }

  def getGroupFromIndex(eventName: String): Option[String] = {
    getIndex()
      .find {
        case (pattern, _) =>
          eventName matches pattern
      }
      .map(_._2)
  }

  // TODO: performance test
  def resolveEvent(event: Event): Event = {
    if (event.metadata.group.isDefined) return event
    if (event.metadata.name.isEmpty) return event.withGroup(defaultGroup)

    val eventName = event.metadata.name.get
    var group = ""
    if (useCache) {
      val cachedGroup = cached.get(eventName)
      if (cachedGroup != null) {
        group = cachedGroup
      } else {
        group = getGroupFromIndex(eventName).getOrElse(defaultGroup)
        cached.put(eventName, group)
      }
    } else {
      group = getGroupFromIndex(eventName).getOrElse(defaultGroup)
    }

    return event.withGroup(group)
  }

  override def getHandler()(
      implicit runningContext: TaskRunningContext
  ): Flow[Event, (Status, Event), NotUsed] = {

    init()

    Flow[Event].map(event => {
      Try(resolveEvent(event)) match {
        case Success(newEvent) => (Norm, newEvent)
        case Failure(ex) =>
          logger.error(s"resolve group failed with error $ex")
          (Fail(ex), event)
      }
    })
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {}
}

class EventGroupResolverTransformBuilder() extends TransformTaskBuilder {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): EventGroupResolverTransform = {
    val config = ConfigStringParser
      .convertStringToConfig(configString)
      .withFallback(buildingContext.getSystemConfig().getConfig("task.event-group-resolver"))

    new EventGroupResolverTransform(
      config.getString("default-group"),
      config.getBoolean("use-cache")
    )
  }

}
