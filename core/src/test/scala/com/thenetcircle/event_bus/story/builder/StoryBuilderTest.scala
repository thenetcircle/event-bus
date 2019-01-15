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

package com.thenetcircle.event_bus.story.builder

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.story.Story.OpExecPos
import com.thenetcircle.event_bus.story.StoryBuilder.StoryInfo
import com.thenetcircle.event_bus.story.interfaces._
import com.thenetcircle.event_bus.story.tasks.http.{HttpSink, HttpSinkSettings}
import com.thenetcircle.event_bus.story.tasks.kafka.{KafkaSource, KafkaSourceSettings}
import com.thenetcircle.event_bus.story.tasks.operators.{DecouplerBidiOperator, _}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._

import scala.concurrent.duration._

class StoryBuilderTest extends TestBase {

  behavior of "StoryBuilder"

  val buildersConfig: Config = appContext.getSystemConfig().getConfig("app.task.builders")

  buildersConfig
    .as[Option[List[String]]]("source")
    .foreach(_.foreach(storyBuilder.addTaskBuilder[ISource]))
  buildersConfig
    .as[Option[List[String]]]("operators")
    .foreach(_.foreach(storyBuilder.addTaskBuilder[IOperator]))
  buildersConfig.as[Option[List[String]]]("sink").foreach(_.foreach(storyBuilder.addTaskBuilder[ISink]))

  it should "build correct Story based on config" in {

    val storyInfo = StoryInfo(
      name = "testStory",
      settings = "",
      source =
        """kafka#{"bootstrap-servers":"localhost:9092,localhost:9093","topics":["event-test-filter","event-test-user","event-test-default"],"topic-pattern":"","max-concurrent-partitions":100,"commit-max-batches":20,"poll-interval":"50ms","wakeup-timeout":"3s","max-wakeups":10,"properties":{}}""",
      sink =
        """http#{"default-request":{"method":"POST","uri":"http://localhost:3001"},"min-backoff":"1 s","max-backoff":"30 s","max-retrytime":"12 h","concurrent-retries":1}""",
      operators = Some(
        """filter#{"event-name-white-list":["event-name-1","event-name-2"],"event-name-black-list":["event-name-3"],"channel-white-list":["channel-1","channel-2"],"channel-black-list":["channel-3"],"allowed-transport-modes":["ASYNC","BOTH","NONE"],"only-extras":{"actorId":"test","generatorId":"tnc-event-dispatcher"}}"""
          + """|||cassandra:after#{"contact-points":["127.0.0.1"],"port":9066,"parallelism":8}"""
          + """|||file:after#{"path":"/temp/failover.log"}"""
          + """|||decoupler#{"buffer-size": 5000,"terminate-delay": "30 m"}"""
      )
    )

    val story = storyBuilder.buildStory(storyInfo)

    story.settings.name shouldEqual "testStory"

    story.source shouldBe a[KafkaSource]
    story.source.asInstanceOf[KafkaSource].settings shouldEqual KafkaSourceSettings(
      "localhost:9092,localhost:9093",
      None,
      Left(Set("event-test-filter", "event-test-user", "event-test-default")),
      pollInterval = Some(FiniteDuration(50, "ms")),
      wakeupTimeout = Some(FiniteDuration(3, "s")),
      maxWakeups = Some(10),
      properties = Map("enable.auto.commit" -> "false")
    )

    story.sink shouldBe a[HttpSink]
    story.sink.asInstanceOf[HttpSink].settings shouldEqual HttpSinkSettings(
      HttpRequest(HttpMethods.POST, Uri("http://localhost:3001"))
    )

    story.operators.get.apply(0)._1 shouldEqual OpExecPos.BeforeSink
    story.operators.get.apply(0)._2 shouldBe a[FilterOperator]
    story.operators.get.apply(0)._2.asInstanceOf[FilterOperator].settings shouldEqual FilterOperatorSettings(
      eventNameWhiteList = Seq("event-name-1", "event-name-2"),
      eventNameBlackList = Seq("event-name-3"),
      channelWhiteList = Seq("channel-1", "channel-2"),
      channelBlackList = Seq("channel-3"),
      allowedTransportModes = Seq("ASYNC", "BOTH", "NONE"),
      onlyExtras = Map("actorId" -> "test", "generatorId" -> "tnc-event-dispatcher")
    )

    story.operators.get.apply(1)._1 shouldEqual OpExecPos.AfterSink
    story.operators.get.apply(1)._2 shouldBe a[CassandraOperator]
    story.operators.get.apply(1)._2.asInstanceOf[CassandraOperator].settings shouldEqual CassandraOperatorSettings(
      List("127.0.0.1"),
      port = 9066,
      parallelism = 8
    )

    story.operators.get.apply(2)._1 shouldEqual OpExecPos.AfterSink
    story.operators.get.apply(2)._2 shouldBe a[FileOperator]
    story.operators.get.apply(2)._2.asInstanceOf[FileOperator].settings shouldEqual FileOperatorSettings(
      path = "/temp/failover.log"
    )

    story.operators.get.apply(3)._1 shouldEqual OpExecPos.BeforeSink
    story.operators.get.apply(3)._2 shouldBe a[IBidiOperator]
    story.operators.get.apply(3)._2 shouldBe a[DecouplerBidiOperator]
    story.operators.get.apply(3)._2.asInstanceOf[DecouplerBidiOperator].settings shouldEqual DecouplerSettings(
      bufferSize = 5000,
      terminateDelay = 30 minutes
    )
  }

}
