package com.thenetcircle.event_bus

import akka.actor.ActorSystem

object AppContext {

  private var inited = false
  private var debug: Boolean = false
  private var env: String = "unknown"
  private var version: String = "unknown"
  private var actorSystem: ActorSystem = _

  def init(verion: String)(implicit system: ActorSystem): Unit = if (!inited) {
    version = verion
    actorSystem = system
    debug = system.settings.config.getBoolean("app.debug")
    env = system.settings.config.getString("app.env")
    inited = true
  }

  def isDebug(): Boolean = debug

  def getEnv(): String = env
  def isDev(): Boolean = env.toUpperCase == "DEVELOPMENT"
  def isProd(): Boolean = env.toUpperCase == "PRODUCTION"

  def getVersion(): String = version

  def getActorSystem(): ActorSystem = actorSystem

}
