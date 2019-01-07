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

import com.thenetcircle.event_bus.{AppContext, BuildInfo}
import kamon.Kamon

object Monitor {

  private var isKamonEnabled: Boolean  = false
  private var isSentryEnabled: Boolean = false

  def init()(implicit appContext: AppContext): Unit = {
    val config = appContext.getSystemConfig()
    isKamonEnabled = config.getBoolean("app.monitor.kamon.enable")
    isSentryEnabled = config.getBoolean("app.monitor.sentry.enable")

    if (isKamonEnabled) {
      Kamon.start()
      appContext.addShutdownHook(Kamon.shutdown())
    }

    if (isSentryEnabled) {
      import io.sentry.Sentry
      val dsn = config.getString("app.monitor.sentry.dsn")
      if (dsn.nonEmpty) {

        val release      = BuildInfo.version
        val environment  = appContext.getAppEnv()
        val tags         = s"app_name:${appContext.getAppName()}"
        val app_packages = "com.thenetcircle.event_bus"

        Sentry.init(
          s"$dsn?release=$release&environment=$environment&tags=$tags&stacktrace.app.packages=$app_packages"
        )

      }
    }
  }

  def isEnabled(): Boolean = isKamonEnabled

}
