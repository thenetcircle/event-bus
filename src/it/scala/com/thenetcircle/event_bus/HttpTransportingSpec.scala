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

package com.thenetcircle.event_bus
import com.thenetcircle.event_bus.dispatcher.{Dispatcher, DispatcherSettings}
import com.thenetcircle.event_bus.testkit.IntegrationSpec
import com.thenetcircle.event_bus.transporter.{Transporter, TransporterSettings}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class HttpTransportingSpec extends IntegrationSpec {

  behavior of "HttpTransporting"

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    val transporter = Transporter(
      TransporterSettings(
        system.settings.config.getConfig("event-bus.test-transporter")))
    transporter.run()

    val dispatcher = Dispatcher(
      DispatcherSettings(
        system.settings.config.getConfig("event-bus.test-dispatcher")))
    dispatcher.run()
  }

  it should "properly transport to kafka pipeline" in {

    println("abc")

    Await.ready(system.whenTerminated, Duration.Inf)

  }

}
