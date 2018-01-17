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

import akka.{Done, NotUsed}
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.interface.{TransformTask, TransformTaskBuilder}
import com.thenetcircle.event_bus.helper.ConfigStringParser
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import java.util.concurrent.ConcurrentHashMap

import com.thenetcircle.event_bus.context.TaskRunningContext

import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

class TopicResolverTransform(defaultTopic: String) extends TransformTask with StrictLogging {

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
      case (topic, submap) =>
        submap
          .get("patterns")
          .foreach(_.split(Regex.quote(ConfigStringParser.delimiter)).foreach(pattern => {
            _index += (pattern -> topic)
          }))
    }
    updateIndex(_index.toMap)
  }

  // TODO: performance test
  def resolveEvent(event: Event): Event = {
    val eventName = event.metadata.name
    var topic = ""

    val cachedTopic = cached.get(eventName)
    if (cachedTopic != null) {
      topic = cachedTopic
    } else {
      val channelOption = index
        .find {
          case (pattern, _) =>
            eventName matches pattern
        }
        .map(_._2)
      topic = channelOption.getOrElse(defaultTopic)
      cached.put(eventName, topic)
    }

    event.withChannel(topic)
  }

  override def getHandler()(
      implicit runningContext: TaskRunningContext
  ): Flow[Event, (Try[Done], Event), NotUsed] = {

    init()

    Flow[Event].map(event => {
      Try(resolveEvent(event)) match {
        case Success(newEvent) => (Success(Done), newEvent)
        case Failure(ex) =>
          logger.error(s"resolve topic failed with error $ex")
          (Failure(ex), event)
      }
    })
  }

}

class TopicResolverTransformBuilder() extends TransformTaskBuilder {

  override def build(configString: String): TopicResolverTransform = {
    val defaultConfig: Config =
      ConfigStringParser.convertStringToConfig("""
      |{
      |  "default_topic": "event-default"
      |}
    """.stripMargin)

    val config = ConfigStringParser.convertStringToConfig(configString).withFallback(defaultConfig)

    new TopicResolverTransform(config.getString("default_topic"))
  }

}
