package com.thenetcircle.event_bus.plots.ops

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.RunningContext
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.interface.{IOp, IOpBuilder}

import scala.util.matching.Regex

class TopicResolver(_topicMapping: Map[String, String], defaultTopic: String) extends IOp {

  private var topicMapping = _topicMapping

  // TODO: lock
  def updateTopicMapping(_topicMapping: Map[String, String]): Unit =
    topicMapping = _topicMapping

  private def resolveTopic(event: Event): Event = {
    val RMatch = new Regex(event.metadata.name)
    val topicOption = topicMapping.collectFirst {
      case (RMatch(_), v) => v
    }
    event.withChannel(topicOption.getOrElse(topicMapping.getOrElse("all", defaultTopic)))
  }

  override def getGraph(): Flow[Event, Event, NotUsed] = Flow[Event].map(resolveTopic)

}

class TopicResolverBuilder extends IOpBuilder {

  /**
    * Builds TopicResolver
    *
    * examples:
    * {
    *   "community_name": "...",
    *   "default_topic": "..."
    * }
    */
  override def build(configString: String)(implicit runningContext: RunningContext) = {

    val config = convertStringToConfig(configString)

    val communityName = config.getString("community_name")
    val defaultTopic = config.getString("default_topic")

    val _mapping: Map[String, String] = Map.empty

    new TopicResolver(_mapping, defaultTopic)

  }

}
