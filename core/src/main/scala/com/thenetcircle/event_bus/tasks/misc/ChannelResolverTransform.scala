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
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.helper.ConfigStringParser
import com.thenetcircle.event_bus.interface.TaskResult.NoResult
import com.thenetcircle.event_bus.interface.{TransformTask, TransformTaskBuilder}
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

class ChannelResolverTransform(defaultChannel: String, useCache: Boolean = false)
    extends TransformTask
    with StrictLogging {

  private var inited: Boolean = false
  private var index: Map[String, String] = Map.empty
  private val cached: ConcurrentHashMap[String, String] = new ConcurrentHashMap()

  def init(): Unit = if (!inited) {
    // TODO: get data from zookeeper
    inited = true
  }

  def getIndex(): Map[String, String] = synchronized { index }
  def updateIndex(_index: Map[String, String]): Unit = synchronized { index = _index }
  def updateMapping(_mapping: Map[String, Map[String, String]]): Unit = {
    val _index = mutable.Map.empty[String, String]
    _mapping.foreach {
      case (channel, submap) =>
        submap
          .get("patterns")
          .foreach(_.split(Regex.quote(ConfigStringParser.delimiter)).foreach(pattern => {
            _index += (pattern -> channel)
          }))
    }
    updateIndex(_index.toMap)
    if (useCache) cached.clear()
  }

  def getChannelFromIndex(eventName: String): Option[String] = {
    getIndex()
      .find {
        case (pattern, _) =>
          eventName matches pattern
      }
      .map(_._2)
  }

  // TODO: performance test
  def resolveEvent(event: Event): Event = {
    val eventName = event.metadata.name
    var channel = ""

    if (useCache) {
      val cachedChannel = cached.get(eventName)
      if (cachedChannel != null) {
        channel = cachedChannel
      } else {
        channel = getChannelFromIndex(eventName).getOrElse(defaultChannel)
        cached.put(eventName, channel)
      }
    } else {
      channel = getChannelFromIndex(eventName).getOrElse(defaultChannel)
    }

    event.withChannel(channel)
  }

  override def getHandler()(
      implicit runningContext: TaskRunningContext
  ): Flow[Event, (ResultTry, Event), NotUsed] = {

    init()

    Flow[Event].map(event => {
      Try(resolveEvent(event)) match {
        case Success(newEvent) => (Success(NoResult), newEvent)
        case Failure(ex) =>
          logger.error(s"resolve topic failed with error $ex")
          (Failure(ex), event)
      }
    })
  }

}

class ChannelResolverTransformBuilder() extends TransformTaskBuilder {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): ChannelResolverTransform = {
    val config = ConfigStringParser
      .convertStringToConfig(configString)
      .withFallback(buildingContext.getSystemConfig().getConfig("task.channel-resolver"))

    new ChannelResolverTransform(
      config.getString("default-channel"),
      config.getBoolean("use-cache")
    )
  }

}
