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

package com.thenetcircle.event_bus.story.tasks.misc

import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.EventStatus.{FAIL, NORM}
import com.thenetcircle.event_bus.misc.{Logging, Util, ZKManager}
import com.thenetcircle.event_bus.story.interfaces.{ITransformTask, ITransformTaskBuilder}
import com.thenetcircle.event_bus.story.{Payload, StoryMat}
import org.apache.curator.framework.recipes.cache.NodeCache
import spray.json._

import scala.collection.mutable.ArrayBuffer
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

case class TopicInfo(topic: String, patterns: Option[List[String]], channels: Option[List[String]])

object TopicInfoProtocol extends DefaultJsonProtocol {
  implicit val topicInfoFormat = jsonFormat3(TopicInfo)
}

class TNCKafkaTopicResolver(zkManager: ZKManager, val _defaultTopic: String) extends ITransformTask with Logging {

  import TopicInfoProtocol._

  private var inited: Boolean              = false
  private var defaultTopic: String         = _defaultTopic
  private var zkWatcher: Option[NodeCache] = None

  private var nameIndex: Map[String, String]    = Map.empty
  private var channelIndex: Map[String, String] = Map.empty

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
    topic = topic.replaceAll(Regex.quote("""{story_name}"""), getStoryName())
    topic
  }

  private def updateAndWatchIndex()(
      implicit runningContext: TaskRunningContext
  ): Unit = {
    zkManager.ensurePath("topics")
    zkWatcher = Some(
      zkManager.watchData("topics") {
        _ foreach {
          data =>
            if (data.nonEmpty) {
              val topicInfo        = data.parseJson.convertTo[List[TopicInfo]]
              val nameIndexList    = ArrayBuffer.empty[(String, String)]
              val channelIndexList = ArrayBuffer.empty[(String, String)]

              topicInfo
                .foreach(info => {
                  // val _topic = replaceSubstitutes(info.topic)
                  val _topic = info.topic
                  info.patterns.foreach(_.foreach(s => {
                    nameIndexList += (s -> _topic)
                  }))
                  info.channels.foreach(_.foreach(s => {
                    channelIndexList += (s -> _topic)
                  }))
                })

              updateIndex(nameIndexList.toMap, channelIndexList.toMap)
            }
        }
      }
    )
  }

  def updateIndex(_nameIndex: Map[String, String], _channelIndex: Map[String, String]): Unit = {
    logger.info("updating topic mapping, nameIndex : " + _nameIndex + ", channelIndex: " + _channelIndex)
    nameIndex = _nameIndex
    channelIndex = _channelIndex
  }

  def getNameIndex(): Map[String, String] = nameIndex

  def getChannelIndex(): Map[String, String] = channelIndex

  override def flow()(
      implicit runningContext: TaskRunningContext
  ): Flow[Payload, Payload, StoryMat] = {
    init()
    Flow[Payload].map {
      case (NORM, event) =>
        Try(resolveEvent(event)) match {
          case Success(newEvent) =>
            (NORM, newEvent)
          case Failure(ex) =>
            producerLogger.error(s"resolve kafka topic failed with error $ex")
            (FAIL(ex, getTaskName()), event)
        }
      case others => others
    }
  }

  def getTopicFromIndex(event: Event): Option[String] = {
    var result: Option[String] = None

    if (event.metadata.channel.isDefined) {
      result = getChannelIndex()
        .find {
          case (pattern, _) =>
            event.metadata.channel.get matches pattern
        }
        .map(_._2)
    }
    if (result.isEmpty && event.metadata.name.isDefined) {
      result = getNameIndex()
        .find {
          case (pattern, _) =>
            event.metadata.name.get matches pattern
        }
        .map(_._2)
    }

    result
  }

  def resolveEvent(event: Event): Event = {
    if (event.metadata.topic.isDefined) {
      producerLogger.info(
        s"event ${event.uuid} has topic ${event.metadata.topic.get} already, will not resolve it."
      )
      return event
    }
    if (event.metadata.name.isEmpty && event.metadata.channel.isEmpty) {
      producerLogger.info(
        s"event ${event.uuid} has no name and channel, will be send to default topic $defaultTopic."
      )
      return event.withTopic(defaultTopic)
    }

    val newTopic = getTopicFromIndex(event).getOrElse(defaultTopic)
    producerLogger.info(s"event ${event.uuid} has been resolved to new topic $newTopic")

    event.withTopic(newTopic)
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    logger.info(s"shutting down TNCKafkaTopicResolver of story ${getStoryName()}.")
    nameIndex = Map.empty
    channelIndex = Map.empty
    zkWatcher.foreach(_.close())
  }
}

class TNCKafkaTopicResolverBuilder() extends ITransformTaskBuilder {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): TNCKafkaTopicResolver = {
    val config = Util
      .convertJsonStringToConfig(configString)
      .withFallback(buildingContext.getSystemConfig().getConfig("task.tnc-topic-resolver"))

    val zkMangerOption = buildingContext.getAppContext().getZKManager()
    if (zkMangerOption.isEmpty) {
      throw new IllegalArgumentException("ZooKeeperManager is required for TNCKafkaTopicResolver")
    }

    new TNCKafkaTopicResolver(
      zkMangerOption.get,
      config.getString("default-topic")
    )
  }

}
