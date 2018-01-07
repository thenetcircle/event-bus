package com.thenetcircle.event_bus

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable.ListBuffer

final class AppContext(appName: String,
                       version: String,
                       debug: Boolean = false,
                       appEnv: String,
                       appConfig: Config) {

  def getEnv(): String = appEnv
  def getName(): String = appName
  def getVersion(): String = version
  def getConfig(): Config = appConfig

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

  def apply(verion: String): AppContext = {
    val appConfig = ConfigFactory.load()
    val appName = appConfig.getString("app.name")

    new AppContext(
      appName,
      verion,
      appConfig.getBoolean("app.debug"),
      appConfig.getString("app.env"),
      appConfig
    )
  }

}
