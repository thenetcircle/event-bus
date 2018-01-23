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

import java.util.concurrent.ConcurrentHashMap

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.misc.{Util, ZKManager}
import com.thenetcircle.event_bus.interfaces.EventStatus.{Fail, Norm}
import com.thenetcircle.event_bus.interfaces.{
  Event,
  EventStatus,
  TransformTask,
  TransformTaskBuilder
}
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

class TNCKafkaTopicResolver(zkManager: ZKManager,
                            val defaultTopic: String,
                            val useCache: Boolean = false)
    extends TransformTask
    with StrictLogging {

  private var inited: Boolean = false
  private var index: Map[String, String] = Map.empty
  private val cached: ConcurrentHashMap[String, String] = new ConcurrentHashMap()

  def init(): Unit = if (!inited) {
    fetchAndUpdateMapping()
    inited = true
  }

  private def fetchAndUpdateMapping(): Unit = {
    val mapping =
      zkManager
        .getChildren("topics")
        .map(_.map(topic => {
          zkManager.getChildrenData(s"topics/$topic").map(l => (topic, l))
        }).filter(_.isDefined).map(_.get).toMap)

    if (mapping.isEmpty) {
      logger.warn("get empty mapping from zookeeper")
    } else {
      logger.debug(s"get mapping from zookeeper, will update index: $mapping")
      updateMapping(mapping.get)
    }
  }

  def getIndex(): Map[String, String] = synchronized { index }
  def updateIndex(_index: Map[String, String]): Unit = synchronized { index = _index }
  def updateMapping(_mapping: Map[String, Map[String, String]]): Unit = {
    val _index = mutable.Map.empty[String, String]
    _mapping.foreach {
      case (topic, submap) =>
        submap
          .get("patterns")
          .foreach(_.split(Regex.quote(Util.configDelimiter)).foreach(pattern => {
            _index += (pattern -> topic)
          }))
    }
    updateIndex(_index.toMap)
    if (useCache) cached.clear()
  }

  override def prepare()(
      implicit runningContext: TaskRunningContext
  ): Flow[Event, (EventStatus, Event), NotUsed] = {

    init()

    Flow[Event].map(event => {
      Try(resolveEvent(event)) match {
        case Success(newEvent) => (Norm, newEvent)
        case Failure(ex) =>
          logger.error(s"resolve topic failed with error $ex")
          (Fail(ex), event)
      }
    })
  }

  def getTopicFromIndex(eventName: String): Option[String] = {
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
    if (event.metadata.name.isEmpty) return event.withGroup(defaultTopic)

    val eventName = event.metadata.name.get
    var topic = ""
    if (useCache) {
      val cachedTopic = cached.get(eventName)
      if (cachedTopic != null) {
        topic = cachedTopic
      } else {
        topic = getTopicFromIndex(eventName).getOrElse(defaultTopic)
        cached.put(eventName, topic)
      }
    } else {
      topic = getTopicFromIndex(eventName).getOrElse(defaultTopic)
    }

    return event.withGroup(topic)
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    index = Map.empty
    cached.clear()
  }
}

class TNCKafkaTopicResolverBuilder() extends TransformTaskBuilder {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): TNCKafkaTopicResolver = {
    val config = Util
      .convertJsonStringToConfig(configString)
      .withFallback(buildingContext.getSystemConfig().getConfig("task.tnc-topic-resolver"))

    val zkManger = ZKManager.getInstance()

    new TNCKafkaTopicResolver(
      zkManger,
      config.getString("default-topic"),
      config.getBoolean("use-cache")
    )
  }

}
