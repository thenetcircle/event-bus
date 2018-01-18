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

import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{KillSwitch, KillSwitches}
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.base.AkkaStreamTest
import com.thenetcircle.event_bus.context.TaskRunningContext
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.interface.{SinkTask, SourceTask}

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{Success, Try}

class StoryTest extends AkkaStreamTest {

  behavior of "Story"

  ignore should "works from HttpSource to KafkaSink" in {

    val settings = StorySettings("test-story")

    val sourceTask = storyBuilder.buildSourceTask("http", """
        |{
        |  "port": 8093,
        |  "succeeded-response": "oooooo"
        |}
      """.stripMargin).get

    val sinkTask = storyBuilder
      .buildSinkTask(
        "kafka",
        """
        |{
        |  "bootstrap-servers": "maggie-kafka-1:9093,maggie-kafka-2:9093,maggie-kafka-3:9093"
        |}
      """.stripMargin
      )
      .get

    val channelResolver =
      storyBuilder.buildTransformTask("channel-resolver", """
        |{
        |  "default-channel": "eventbus-test"
        |}
      """.stripMargin).map(t => List(t))

    val story = new Story(settings, sourceTask, sinkTask, channelResolver)
    val (killSwitch, doneFuture) = story.run()

    StdIn.readLine()

  }

  it should "works from KafkaSource to HttpSink" in {

    val settings = StorySettings("test-story-2")

    val sourceTask = storyBuilder
      .buildSourceTask(
        "kafka",
        """
          |{
          |  "bootstrap-servers": "maggie-kafka-1:9093,maggie-kafka-2:9093,maggie-kafka-3:9093",
          |  "group-id": "eventbus-testgroup",
          |  "topics": [ "eventbus-test" ]
          |}
        """.stripMargin
      )
      .get

    val sinkTask = storyBuilder
      .buildSinkTask("http", """
            |{
            |  "request" : {
            |    "uri": "http://127.0.0.1:3001"
            |  },
            |  "expected-response": "ok"
            |}
          """.stripMargin)
      .get

    val story = new Story(settings, sourceTask, sinkTask)
    val (killSwitch, doneFuture) = story.run()

    // Await.result(system.whenTerminated, 10.minutes)
    StdIn.readLine()

  }

  ignore should "works from MockSource to HttpSink" in {

    val settings = StorySettings("test-story-3")

    val sourceTask = new SourceTask {
      override def runWith(
          handler: Flow[(Try[Done], Event), (Try[Done], Event), NotUsed]
      )(implicit runningContext: TaskRunningContext): (KillSwitch, Future[Done]) = {

        val testEvent1 = createTestEvent("test-event-1")
        val testEvent2 = createTestEvent("test-event-2")
        val testEvent3 = createTestEvent("test-event-3")

        val data = List(
          (Success(Done), testEvent1),
          (Success(Done), testEvent2),
          (Success(Done), testEvent3)
        )

        Source(data)
          .viaMat(KillSwitches.single)(Keep.right)
          .via(handler)
          .toMat(Sink.foreach(println))(Keep.both)
          .run()

      }
    }

    val sinkTask = storyBuilder
      .buildSinkTask("http", """
                                 |{
                                 |  "request" : {
                                 |    "uri": "http://127.0.0.1:3001"
                                 |  },
                                 |  "expected-response": "ok"
                                 |}
                               """.stripMargin)
      .get

    val story = new Story(settings, sourceTask, sinkTask)
    val (killSwitch, doneFuture) = story.run()

    // Await.result(system.whenTerminated, 60.seconds)
    StdIn.readLine()

  }

  ignore should "works from HttpSource to Std Output" in {

    val settings = StorySettings("test-story-4")

    val sourceTask = storyBuilder.buildSourceTask("http", """
        |{
        |  "interface": "127.0.0.1",
        |  "port": 8092,
        |  "succeeded-response": "okoo",
        |  "max-connections": 1000,
        |  "request-timeout": "5s"
        |}
      """.stripMargin).get

    val sinkTask = new SinkTask {
      override def getHandler()(implicit runningContext: TaskRunningContext) = {
        Flow[Event].map(e => {
          println(s"Accept new event $e")
          (Success(Done), e)
        })
      }
    }

    val story = new Story(settings, sourceTask, sinkTask)
    val (killSwitch, doneFuture) = story.run()

    // Await.result(system.whenTerminated, 10.minutes)
    StdIn.readLine()

  }

}
