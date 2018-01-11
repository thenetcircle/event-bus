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

package com.thenetcircle.event_bus.misc

import com.typesafe.config.{Config, ConfigFactory}

class ExecutionEnvironment(appName: String,
                           appVersion: String,
                           appEnv: String,
                           debug: Boolean,
                           systemConfig: Config,
                           executorGroup: String)
    extends Environment(
      appName: String,
      appVersion: String,
      appEnv: String,
      debug: Boolean,
      systemConfig: Config
    ) {

  def getExecutorGroup(): String = executorGroup

}

object ExecutionEnvironment {

  def apply(args: Array[String]): ExecutionEnvironment = apply(args, ConfigFactory.load())

  def apply(args: Array[String], systemConfig: Config): ExecutionEnvironment = {

    systemConfig.checkValid(ConfigFactory.defaultReference(), "app")

    // Check Executor Name
    var executorGroup: String = if (args.length > 0) args(0) else ""
    if (executorGroup.isEmpty)
      executorGroup = systemConfig.getString("app.default-executor-group")

    val appName = systemConfig.getString("app.name")

    new ExecutionEnvironment(
      appName,
      systemConfig.getString("app.version"),
      systemConfig.getString("app.env"),
      systemConfig.getBoolean("app.debug"),
      systemConfig,
      executorGroup
    )
  }

}
