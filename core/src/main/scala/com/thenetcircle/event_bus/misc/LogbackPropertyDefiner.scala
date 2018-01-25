package com.thenetcircle.event_bus.misc

import ch.qos.logback.core.PropertyDefinerBase
import com.typesafe.config.ConfigFactory

class LogbackPropertyDefiner extends PropertyDefinerBase{
  private var propertyName: Option[String] = None

  override def getPropertyValue(): String = {
    propertyName.map(p => ConfigFactory.load().getString(p)).getOrElse("")
  }

  def setPropertyName(propertyName: String): Unit = {
    this.propertyName = Some(propertyName)
  }
}
