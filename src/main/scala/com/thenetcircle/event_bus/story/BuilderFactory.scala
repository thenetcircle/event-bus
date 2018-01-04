package com.thenetcircle.event_bus.story

import com.thenetcircle.event_bus.interface.{OpPlotBuilder, SinkPlotBuilder, SourcePlotBuilder}
import com.thenetcircle.event_bus.plots.http.{HttpSinkBuilder, HttpSourceBuilder}
import com.thenetcircle.event_bus.plots.kafka.{KafkaSinkBuilder, KafkaSourceBuilder}

object BuilderFactory {

  def getSourcePlotBuilder(plotType: String): Option[SourcePlotBuilder] =
    plotType.toUpperCase match {
      case "HTTP"  => Some(new HttpSourceBuilder())
      case "KAFKA" => Some(new KafkaSourceBuilder())
      case _       => None
    }

  def getOpPlotBuilder(plotType: String): Option[OpPlotBuilder] = plotType.toUpperCase match {
    case "TOPIC_RESOLVER" => None
    case _                => None
  }

  def getSinkPlotBuilder(plotType: String): Option[SinkPlotBuilder] = plotType.toUpperCase match {
    case "HTTP"  => Some(new HttpSinkBuilder())
    case "KAFKA" => Some(new KafkaSinkBuilder())
    case _       => None
  }

}
