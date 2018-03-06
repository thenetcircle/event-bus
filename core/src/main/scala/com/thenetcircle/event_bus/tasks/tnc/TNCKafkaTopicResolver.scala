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
import java.util.concurrent.atomic.AtomicBoolean

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.interfaces.EventStatus.{Fail, Norm}
import com.thenetcircle.event_bus.interfaces.{Event, EventStatus, TransformTask, TransformTaskBuilder}
import com.thenetcircle.event_bus.misc.{Util, ZooKeeperManager}
import com.typesafe.scalalogging.StrictLogging
import org.apache.curator.framework.recipes.cache.NodeCache
import spray.json._

import scala.collection.mutable
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

case class TopicInfo(topic: String, patterns: List[String])

object TopicInfoProtocol extends DefaultJsonProtocol {
  implicit val topicInfoFormat = jsonFormat2(TopicInfo)
}

class TNCKafkaTopicResolver(zkManager: ZooKeeperManager, val _defaultTopic: String, val useCache: Boolean = false)
    extends TransformTask
    with StrictLogging {

  import TopicInfoProtocol._

  private var inited: Boolean                              = false
  private var defaultTopic: String                         = _defaultTopic
  private var index: mutable.LinkedHashMap[String, String] = mutable.LinkedHashMap.empty
  private val cached: ConcurrentHashMap[String, String]    = new ConcurrentHashMap()
  private var zkWatcher: Option[NodeCache]                 = None

  def init()(
      implicit runningContext: TaskRunningContext
  ): Unit = if (!inited) {
    defaultTopic = replaceSubstitutes(defaultTopic)
    updateAndWatchIndex()
    inited = true
  }

  def replaceSubstitutes(_topic: String)(
      implicit runningContext: TaskRunningContext
  ): String = {
    var topic = _topic
    topic = topic.replaceAll(Regex.quote("""{app_name}"""), runningContext.getAppContext().getAppName())
    topic = topic.replaceAll(Regex.quote("""{app_env}"""), runningContext.getAppContext().getAppEnv())
    topic = topic.replaceAll(Regex.quote("""{story_name}"""), runningContext.getStoryName())
    topic
  }

  val zkInited = new AtomicBoolean(false)

  private def updateAndWatchIndex()(
      implicit runningContext: TaskRunningContext
  ): Unit = {
    zkManager.ensurePath("topics")
    zkWatcher = Some(
      zkManager.watchData("topics") {
        _ foreach { data =>
          val topicInfo = data.parseJson.convertTo[List[TopicInfo]]
          val index     = mutable.LinkedHashMap.empty[String, String]
          topicInfo
            .foreach(info => {
              val _topic = replaceSubstitutes(info.topic)
              info.patterns.foreach(pat => {
                index += (pat -> _topic)
              })
            })
          updateIndex(index)
        }
      }
    )
  }

  def getIndex(): mutable.LinkedHashMap[String, String] = synchronized {
    index
  }

  def updateIndex(_index: mutable.LinkedHashMap[String, String]): Unit = synchronized {
    logger.info("updating topic mapping " + _index)
    index = _index
    if (useCache) cached.clear()
  }

  override def prepare()(
      implicit runningContext: TaskRunningContext
  ): Flow[Event, (EventStatus, Event), NotUsed] = {
    init()
    Flow[Event].map(event => {
      Try(resolveEvent(event)) match {
        case Success(newEvent) =>
          (Norm, newEvent)
        case Failure(ex) =>
          logger.error(s"resolve kafka topic failed with error $ex")
          (Fail(ex), event)
      }
    })
  }

  def getTopicFromIndex(eventName: String): Option[String] =
    getIndex()
      .find {
        case (pattern, _) =>
          eventName matches pattern
      }
      .map(_._2)

  // TODO: performance test
  def resolveEvent(event: Event): Event = {
    if (event.metadata.group.isDefined) {
      logger.debug(s"event ${event.uuid} has group ${event.metadata.group.get} already, will not be resolving again.")
      return event
    }
    if (event.metadata.name.isEmpty) {
      logger.debug(s"event ${event.uuid} has no name, will be send to default topic $defaultTopic.")
      return event.withGroup(defaultTopic)
    }

    val eventName = event.metadata.name.get
    var topic     = ""
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

    logger.debug(s"event ${event.uuid} has been resolved to new topic $topic")
    return event.withGroup(topic)
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    logger.info(s"shutting down TNCKafkaTopicResolver of story ${runningContext.getStoryName()}.")
    index = mutable.LinkedHashMap.empty
    cached.clear()
    zkWatcher.foreach(_.close())
  }
}

class TNCKafkaTopicResolverBuilder() extends TransformTaskBuilder {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): TNCKafkaTopicResolver = {
    val config = Util
      .convertJsonStringToConfig(configString)
      .withFallback(buildingContext.getSystemConfig().getConfig("task.tnc-topic-resolver"))

    val zkMangerOption = buildingContext.getAppContext().getZooKeeperManager()
    if (zkMangerOption.isEmpty) {
      throw new IllegalArgumentException("ZooKeeperManager is required for TNCKafkaTopicResolver")
    }

    new TNCKafkaTopicResolver(
      zkMangerOption.get,
      config.getString("default-topic"),
      config.getBoolean("use-cache")
    )
  }

}
