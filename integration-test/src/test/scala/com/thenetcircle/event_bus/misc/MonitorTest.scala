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

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, Uri}
import com.thenetcircle.event_bus.IntegrationTestBase
import com.thenetcircle.event_bus.story.tasks.http.{HttpSource, HttpSourceSettings}
import com.thenetcircle.event_bus.story.tasks.kafka.{KafkaSink, KafkaSinkSettings}
import com.thenetcircle.event_bus.story.{Story, StorySettings}

import scala.io.StdIn

class MonitorTest extends IntegrationTestBase {

  behavior of "MonitorTest"

  Monitor.init()

  val testSource = new HttpSource(HttpSourceSettings(port = 55650))
  val testSink = new KafkaSink(
    KafkaSinkSettings(config.getString("app.test.kafka.bootstrap-servers"), defaultTopic = "event-bus-monitor-test")
  )
  val story = new Story(StorySettings("monitor-test"), testSource, testSink)

  override def afterAll(): Unit = {
    story.shutdown()
    super.afterAll()
  }

  it should "record metrics correctly" in {

    story.run()

    for (i <- 1 to 1000) {
      Http().singleRequest(HttpRequest(uri = Uri("http://127.0.0.1:55650"), entity = HttpEntity("abcdef")))
      Thread.sleep(1000)
    }

    StdIn.readLine()

  }

}
