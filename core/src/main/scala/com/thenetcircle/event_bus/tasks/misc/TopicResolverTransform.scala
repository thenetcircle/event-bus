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
import com.thenetcircle.event_bus.misc.ConfigStringParser
import com.thenetcircle.event_bus.story.TaskRunningContext
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success, Try}

class TopicResolverTransform(defaultTopic: String) extends TransformTask with StrictLogging {

  private var inited: Boolean = false
  private var mapping: Map[String, Map[String, String]] = Map.empty
  private var index: Map[String, String] = Map.empty

  def init(): Unit = if (!inited) {
    // get data from zookeeper
    inited = true
  }

  def getIndex(): Map[String, String] = synchronized(index)
  def updateIndex(_mapping: Map[String, Map[String, String]]): Unit = synchronized {
    // calculate index
  }

  // TODO: performance test
  def resolveEvent(event: Event): Event = {
    val eventName = event.metadata.name
    val topicOption = getIndex()
      .find { case (topicPattern, _) => eventName matches topicPattern }
      .map(_._2)

    event.withChannel(topicOption.getOrElse(defaultTopic))
  }

  override def getHandler()(
      implicit context: TaskRunningContext
  ): Flow[Event, (Try[Done], Event), NotUsed] =
    Flow[Event].map(event => {
      Try(resolveEvent(event)) match {
        case Success(newEvent) => (Success(Done), newEvent)
        case Failure(ex) =>
          logger.error(s"resolve topic failed with error $ex")
          (Failure(ex), event)
      }
    })

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
