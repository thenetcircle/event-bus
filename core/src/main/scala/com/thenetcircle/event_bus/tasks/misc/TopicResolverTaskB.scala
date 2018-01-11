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

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.interface.{TaskB, TaskBBuilder}
import com.thenetcircle.event_bus.story.TaskExecutingContext
import com.typesafe.config.Config

class TopicResolverTaskB(_topicMapping: Map[String, String], defaultTopic: String) extends TaskB {

  private var topicMapping = _topicMapping

  // TODO: lock
  def updateTopicMapping(_topicMapping: Map[String, String]): Unit =
    topicMapping = _topicMapping

  // TODO: performance test
  def resolveEvent(event: Event): Event = {
    val eventName = event.metadata.name
    val topicOption = topicMapping
      .find { case (key, _) => eventName matches key }
      .map(_._2)
    event.withChannel(topicOption.getOrElse(defaultTopic))
  }

  override def getGraph(): Flow[Event, Event, NotUsed] = Flow[Event].map(resolveEvent)

}

class TopicResolverTaskBBuilder() extends TaskBBuilder {

  val defaultConfig: Config = convertStringToConfig("""
      |{
      |  "default_topic": "event-default"
      |}
    """.stripMargin)

  override def build(configString: String)(implicit context: TaskExecutingContext) = {

    val config = convertStringToConfig(configString).withFallback(defaultConfig)

    val defaultTopic = config.getString("default_topic")
    val _mapping: Map[String, String] = Map.empty

    new TopicResolverTaskB(_mapping, defaultTopic)

  }

}
