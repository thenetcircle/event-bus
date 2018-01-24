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

package com.thenetcircle.event_bus.context

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable.ListBuffer

class AppContext(appName: String, appVersion: String, appEnv: String, debug: Boolean, systemConfig: Config) {

  def getAppEnv(): String       = appEnv
  def getAppName(): String      = appName
  def getAppVersion(): String   = appVersion
  def getSystemConfig(): Config = systemConfig

  def isDebug(): Boolean = debug
  def isDev(): Boolean   = appEnv.toLowerCase == "development" || appEnv.toLowerCase == "dev"
  def isTest(): Boolean  = appEnv.toLowerCase == "test"
  def isProd(): Boolean  = appEnv.toLowerCase == "production" || appEnv.toLowerCase == "prod"

  private val shutdownHooks = new ListBuffer[() => Unit]
  def addShutdownHook(body: => Unit) {
    shutdownHooks += (() => body)
  }
  def shutdown(): Unit =
    for (hook <- shutdownHooks) hook()

}

object AppContext {
  def apply(): AppContext = apply(ConfigFactory.load())

  def apply(systemConfig: Config): AppContext = {
    systemConfig.checkValid(ConfigFactory.defaultReference(), "app")
    val appName = systemConfig.getString("app.name")

    new AppContext(
      appName,
      systemConfig.getString("app.version"),
      systemConfig.getString("app.env"),
      systemConfig.getBoolean("app.debug"),
      systemConfig
    )
  }
}
