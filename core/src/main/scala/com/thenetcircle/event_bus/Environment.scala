package com.thenetcircle.event_bus

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable.ListBuffer

final class Environment(appName: String,
                        appVersion: String,
                        appEnv: String,
                        debug: Boolean,
                        systemConfig: Config) {

  def getAppEnv(): String = appEnv
  def getAppName(): String = appName
  def getAppVersion(): String = appVersion
  def getConfig(): Config = systemConfig
  def getConfig(path: String): Config = systemConfig.getConfig(path)

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

object Environment {

  def apply(): Environment = apply(ConfigFactory.load())

  def apply(systemConfig: Config): Environment = {
    systemConfig.checkValid(ConfigFactory.defaultReference(), "app")

    val appName = systemConfig.getString("app.name")

    new Environment(
      appName,
      systemConfig.getString("app.version"),
      systemConfig.getString("app.env"),
      systemConfig.getBoolean("app.debug"),
      systemConfig
    )
  }

}
