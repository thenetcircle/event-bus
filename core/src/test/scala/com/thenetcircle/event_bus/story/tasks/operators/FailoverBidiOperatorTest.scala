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

package com.thenetcircle.event_bus.story.tasks.operators

import java.util.concurrent.ConcurrentLinkedDeque

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.event.EventStatus.{NORMAL, SKIPPING}
import com.thenetcircle.event_bus.story.interfaces.{ISink, ISinkableTask}
import com.thenetcircle.event_bus.story.{Payload, StoryMat, TaskRunningContext}

import scala.concurrent.Await
import scala.concurrent.duration._

class FailoverBidiOperatorTest extends TestBase {

  behavior of "FailoverBidiOperator"

  it should "works properly" in {

    val secondarySinkResult = new ConcurrentLinkedDeque[Payload]()
    val secondarySink = new ISinkableTask {
      override def flow()(implicit runningContext: TaskRunningContext): Flow[Payload, Payload, StoryMat] =
        Flow[Payload].map(pl => {
          secondarySinkResult.offer(pl)
          pl
        })
    }

    val testSinkResult = new ConcurrentLinkedDeque[Payload]()
    val testSink       = Flow[Payload].map(pl => { secondarySinkResult.offer(pl); pl })

    val operator = new FailoverBidiOperator(
      FailoverBidiOperatorSettings(
        secondarySink = Some(secondarySink)
      )
    )

    val testSource: Source[Payload, NotUsed] = Source(
      List(
        (NORMAL, createTestEvent("norm_event1")),
        (SKIPPING, createTestEvent("skip_event1")),
        (NORMAL, createTestEvent("norm_event2")),
        (SKIPPING, createTestEvent("skip_event2")),
        (NORMAL, createTestEvent("norm_event3")),
        (SKIPPING, createTestEvent("skip_event3"))
      )
    )

    Await.result(testSource.via(testSink.join(operator.flow())).runWith(Sink.ignore), 10 seconds)

    println(testSinkResult)
    println(secondarySinkResult)

  }

}
