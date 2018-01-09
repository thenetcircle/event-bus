package com.thenetcircle.event_bus

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable.ListBuffer

final class AppContext(appName: String,
                       version: String,
                       debug: Boolean = false,
                       appEnv: String,
                       systemConfig: Config) {

  def getEnv(): String = appEnv
  def getName(): String = appName
  def getVersion(): String = version
  def getConfig(): Config = systemConfig

  def isDebug(): Boolean = debug
  def isDev(): Boolean = appEnv.toLowerCase == "development" || appEnv.toLowerCase == "dev"
  def isTest(): Boolean = appEnv.toLowerCase == "test"
  def isProd(): Boolean = appEnv.toLowerCase == "production" || appEnv.toLowerCase == "prod"

  private val shutdownHooks = new ListBuffer[() => Unit]
  def addShutdownHook(body: => Unit) {
    shutdownHooks += (() => body)
  }
  def shutdown(): Unit = {
    for (hook <- shutdownHooks) hook()
  }

}

object AppContext {

  def apply(): AppContext = apply(ConfigFactory.load())

  def apply(systemConfig: Config): AppContext = {
    systemConfig.checkValid(ConfigFactory.defaultReference(), "app")

    val appName = systemConfig.getString("app.name")

    new AppContext(
      appName,
      systemConfig.getString("app.version"),
      systemConfig.getBoolean("app.debug"),
      systemConfig.getString("app.env"),
      systemConfig
    )
  }

}
