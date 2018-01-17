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

package com.thenetcircle.event_bus.story

import akka.stream.{KillSwitch, KillSwitches}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.base.AkkaStreamTest
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.interface.SourceTask

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.Try

class StoryTest extends AkkaStreamTest {

  behavior of "Story"

  it should "works from HttpSource to KafkaSink" in {

    val settings = StorySettings("test-story")

    val sourceTask = builderFactory.buildSourceTask("http", """
        |{
        |  "interface": "127.0.0.1",
        |  "port": 8086,
        |  "succeeded-response": "okoo",
        |  "max-connections": 1000,
        |  "request-timeout": "5s"
        |}
      """.stripMargin).get

    val sinkTask = builderFactory
      .buildSinkTask(
        "kafka",
        """
        |{
        |  "bootstrap-servers": "maggie-kafka-1:9093,maggie-kafka-2:9093,maggie-kafka-3:9093",
        |  "close-timeout": "100s",
        |  "parallelism": 50
        |}
      """.stripMargin
      )
      .get

    val story = new Story(settings, sourceTask, sinkTask)
    val (killSwitch, doneFuture) = story.run()

    /*
    val sinkTask2 = new SinkTask {
      override def getHandler()(
          implicit context: TaskRunningContext
      ): Flow[Event, (Try[Done], Event), NotUsed] = {

        Flow[Event].map((Success(Done), _))

      }
    }

    val route = path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
      }
    }
    val handler: Flow[HttpRequest, HttpResponse, Any] = Flow[HttpRequest].map(
      _ =>
        HttpResponse(
          entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>")
      )
    )

    val fu = Http().bindAndHandle(handler, "127.0.0.1", 8080)
    fu.flatMap(_.unbind())

    val runningContextFactory = TaskRunningContextFactory()
    system.actorOf(StoryRunner.props(runningContextFactory, story), "test-story")*/

    Await.result(system.whenTerminated, 60.seconds)

  }

  it should "works from KafkaSource to HttpSink" in {

    val settings = StorySettings("test-story-2")

    val sourceTask = builderFactory
      .buildSourceTask(
        "kafka",
        """
          |{
          |  "bootstrap-servers": "maggie-kafka-1:9093,maggie-kafka-2:9093,maggie-kafka-3:9093",
          |  "group-id": "eventbus-testgroup",
          |  "topics": [ "event-default" ],
          |  "max-concurrent-partitions": 50,
          |  "properties": {}
          |}
        """.stripMargin
      )
      .get

    val sinkTask = builderFactory
      .buildSinkTask("http", """
            |{
            |  "request" : {
            |    "uri": "http://10.60.53.71:3000"
            |  },
            |  "expected-response": "TEST_RESPONSE"
            |}
          """.stripMargin)
      .get

    val story = new Story(settings, sourceTask, sinkTask)
    val (killSwitch, doneFuture) = story.run()

    // Await.result(system.whenTerminated, 60.seconds)
    StdIn.readLine()

  }

  it should "works from MockSource to HttpSink" in {

    val settings = StorySettings("test-story-3")

    val sourceTask = new SourceTask {
      override def runWith(
          handler: Flow[Event, (Try[Done], Event), NotUsed]
      )(implicit context: TaskRunningContext): (KillSwitch, Future[Done]) = {

        val testEvent1 = createTestEvent("test-event-1")
        val testEvent2 = createTestEvent("test-event-2")
        val testEvent3 = createTestEvent("test-event-3")

        Source(List(testEvent1, testEvent2, testEvent3))
        // .single(testEvent)
          .viaMat(KillSwitches.single)(Keep.right)
          .via(handler)
          .toMat(Sink.foreach(println))(Keep.both)
          .run()

      }
    }

    val sinkTask = builderFactory
      .buildSinkTask("http", """
                                 |{
                                 |  "request" : {
                                 |    "uri": "http://127.0.0.1:8888"
                                 |  },
                                 |  "expected-response": "TEST_RESPONSE"
                                 |}
                               """.stripMargin)
      .get

    val story = new Story(settings, sourceTask, sinkTask)
    val (killSwitch, doneFuture) = story.run()

    // Await.result(system.whenTerminated, 60.seconds)
    StdIn.readLine()

  }

}
