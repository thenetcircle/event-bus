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
import org.apache.curator.framework.recipes.cache.{ChildData, PathChildrenCache}
import org.apache.curator.framework.recipes.cache.PathChildrenCache.StartMode
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type._

import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

class TNCKafkaTopicResolver(zkManager: ZooKeeperManager, val _defaultTopic: String, val useCache: Boolean = false)
    extends TransformTask
    with StrictLogging {

  private var inited: Boolean                           = false
  private var defaultTopic: String                      = _defaultTopic
  private var index: Map[String, String]                = Map.empty
  private val cached: ConcurrentHashMap[String, String] = new ConcurrentHashMap()
  private var zkWatcher: Option[PathChildrenCache]      = None

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
      zkManager.watchChildren("topics", startMode = StartMode.POST_INITIALIZED_EVENT) { (_event, _watcher) =>
        if (_event.getType == INITIALIZED) {
          zkInited.compareAndSet(false, true)
          val _index = createIndexFromZKData(_event.getInitialData.asScala.toList)
          updateIndex(_index)
        } else if (zkInited.get() == true &&
                   (_event.getType == CHILD_ADDED || _event.getType == CHILD_UPDATED || _event.getType == CHILD_REMOVED)) {
          val _index = createIndexFromZKData(_watcher.getCurrentData.asScala.toList)
          updateIndex(_index)
        }
      }
    )
  }

  val delimiter = """|||"""
  def createIndexFromZKData(data: List[ChildData])(
      implicit runningContext: TaskRunningContext
  ): Map[String, String] =
    data
      .flatMap(child => {
        val topicName   = Util.getLastPartOfPath(child.getPath)
        val patternList = Util.makeUTF8String(child.getData).split(Regex.quote(delimiter))
        patternList.map(pat => pat -> replaceSubstitutes(topicName))
      })
      .toMap

  def getIndex(): Map[String, String] = synchronized { index }
  def updateIndex(_index: Map[String, String]): Unit = synchronized {
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
          logger.debug(s"new resolved event $newEvent")
          (Norm, newEvent)
        case Failure(ex) =>
          logger.error(s"resolve topic failed with error $ex")
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
    if (event.metadata.group.isDefined) return event
    if (event.metadata.name.isEmpty) return event.withGroup(defaultTopic)

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

    return event.withGroup(topic)
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    logger.info(s"shutting down TNCKafkaTopicResolver of story ${runningContext.getStoryName()}.")
    index = Map.empty
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
