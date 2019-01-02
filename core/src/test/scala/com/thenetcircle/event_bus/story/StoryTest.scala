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

import akka.NotUsed
import akka.stream.Attributes
import akka.stream.scaladsl.{Flow, Source}
import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.interfaces.EventStatus._
import com.thenetcircle.event_bus.story.Story.Payload

import scala.concurrent.{Await, Future}
import scala.util.Random

class StoryTest extends TestBase {

  behavior of "Story"

  it should "make sure the message order after wrapping tasks" in {

    val slowTask: Flow[Payload, Payload, NotUsed] = Flow[Payload]
      .mapAsync(2) { pl =>
        Future {
          Thread.sleep(Random.nextInt(1000))
          pl
        }
      }

    val slowTask2: Flow[Payload, Payload, NotUsed] = Flow[Payload].async
      .addAttributes(Attributes.inputBuffer(1, 1))

    val wrappedTask: Flow[Payload, Payload, NotUsed] = Story.wrapTask(slowTask2, "testTask")

    val testSource: Source[Payload, NotUsed] = Source(
      List(
        (NORM, createTestEvent("norm_event1")),
        (SKIP, createTestEvent("skip_event1")),
        (NORM, createTestEvent("norm_event2")),
        (SKIP, createTestEvent("skip_event2")),
        (NORM, createTestEvent("norm_event3")),
        (SKIP, createTestEvent("skip_event3"))
      )
    )

    testSource.via(wrappedTask).runForeach(println)

    Thread.sleep(5000)
  }
}
