package com.thenetcircle.event_bus.plots.ops

import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.RunningContext
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.interface.{IOp, IOpBuilder}
import com.typesafe.config.Config

class TopicResolver(_topicMapping: Map[String, String], defaultTopic: String) extends IOp {

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

class TopicResolverBuilder() extends IOpBuilder {

  val defaultConfig: Config = convertStringToConfig("""
      |{
      |  "default_topic": "event-default"
      |}
    """.stripMargin)

  override def build(configString: String)(implicit runningContext: RunningContext) = {

    val config = convertStringToConfig(configString).withFallback(defaultConfig)

    val defaultTopic = config.getString("default_topic")
    val _mapping: Map[String, String] = Map.empty

    new TopicResolver(_mapping, defaultTopic)

  }

}