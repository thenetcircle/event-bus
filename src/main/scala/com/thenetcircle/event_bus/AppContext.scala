package com.thenetcircle.event_bus

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._

final class AppContext(appName: String,
                       version: String,
                       debug: Boolean = false,
                       appEnv: String,
                       appConfig: Config,
                       actorSystem: ActorSystem) {

  def getEnv(): String = appEnv
  def getName(): String = appName
  def getVersion(): String = version
  def getConfig(): Config = appConfig
  def getActorSystem(): ActorSystem = actorSystem

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
    actorSystem.terminate()
    Await.result(actorSystem.whenTerminated, 60.seconds)
  }

}

object AppContext {

  def apply(verion: String): AppContext = {
    val appConfig = ConfigFactory.load()
    val appName = appConfig.getString("app.name")
    val actorSystem = ActorSystem(appName, appConfig)

    new AppContext(
      appName,
      verion,
      appConfig.getBoolean("app.debug"),
      appConfig.getString("app.env"),
      appConfig,
      actorSystem
    )
  }

}
