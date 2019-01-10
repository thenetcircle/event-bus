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
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.thenetcircle.event_bus.TestBase
import com.thenetcircle.event_bus.event.EventStatus.{FAILED, NORMAL, SKIPPING, STAGING}
import com.thenetcircle.event_bus.story.interfaces.IFailoverTask
import com.thenetcircle.event_bus.story.{Payload, StoryMat, TaskRunningContext}
import org.scalatest.BeforeAndAfter

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random
import scala.util.control.NonFatal

class SafeDisconnectorBidiOperatorTest extends TestBase with BeforeAndAfter {

  behavior of "SafeDisconnectorBidiOperator"

  val failoverResult = new ConcurrentLinkedDeque[Payload]()
  val secondarySink = new IFailoverTask {
    override def flow()(implicit runningContext: TaskRunningContext): Flow[Payload, Payload, StoryMat] =
      Flow[Payload].map(pl => {
        failoverResult.offer(pl)
        pl
      })
  }

  val testSource: Source[Payload, NotUsed] = Source(
    List(
      (NORMAL, createTestEvent("event1")),
      (SKIPPING, createTestEvent("event2")),
      (FAILED(new RuntimeException("failed")), createTestEvent("event3")),
      (STAGING(), createTestEvent("event4")),
      (NORMAL, createTestEvent("event5")),
      (STAGING(), createTestEvent("event6"))
    )
  )

  val returnedResult         = new ConcurrentLinkedDeque[Payload]()
  val returnSink             = Flow[Payload].map(returnedResult.offer).toMat(Sink.ignore)(Keep.right)
  val operatedResult         = new ConcurrentLinkedDeque[Payload]()
  val expectedNormalResult   = List("event1", "event2", "event3", "event4", "event5", "event6")
  val expectedFailoverResult = List("event4", "event6")
  val expectedReturnedResult = List("event1", "event2", "event3", "event4", "event5", "event6")

  def resultToList(result: ConcurrentLinkedDeque[Payload]): List[String] =
    result.iterator().asScala.map(_._2.metadata.name.get).toList

  after {
    operatedResult.clear()
    failoverResult.clear()
    returnedResult.clear()
  }

  it should "works with normal providers" in {
    val task = new SafeDisconnectorBidiOperator(
      SafeDisconnectorSettings(
        secondarySink = Some(secondarySink)
      )
    )
    // test normal operation
    val providers = Flow[Payload].map(pl => { operatedResult.offer(pl); pl })
    Await.result(testSource.via(providers.join(task.flow())).runWith(returnSink), 1 seconds)
    resultToList(operatedResult) shouldEqual expectedNormalResult
    resultToList(failoverResult) shouldEqual expectedFailoverResult
    resultToList(returnedResult) shouldEqual expectedReturnedResult
  }

  it should "works with slow providers" in {
    val task = new SafeDisconnectorBidiOperator(
      SafeDisconnectorSettings(
        bufferSize = 2,
        completeDelay = 3 second,
        secondarySink = Some(secondarySink)
      )
    )
    // test slow operation
    val providers = Flow[Payload]
      .mapAsync(2) { pl =>
        Future {
          Thread.sleep(Random.nextInt(1000))
          pl
        }
      }
      .map(pl => {
        operatedResult.offer(pl)
        pl
      })
    try {
      Await.result(testSource.via(providers.join(task.flow())).runWith(returnSink), 6 seconds)
    } catch { case _ => }

    resultToList(operatedResult) shouldEqual List("event1", "event2", "event3", "event4") // 4 get into operated, since buffer size is 2, and operation size is 2, so rest 2 gone to failover
    resultToList(returnedResult) shouldEqual expectedReturnedResult
    resultToList(failoverResult) shouldEqual List("event5", "event6", "event4") // event4 is because it's in STAGING status

    task.shutdown()
  }

  it should "works with blocking providers" in {
    val task = new SafeDisconnectorBidiOperator(
      SafeDisconnectorSettings(
        bufferSize = 3,
        completeDelay = 3 second,
        secondarySink = Some(secondarySink)
      )
    )
    val providers = Flow[Payload]
      .mapAsync(2) { pl =>
        Future {
          operatedResult.offer(pl)
          while (true) Thread.sleep(100)
          pl
        }
      }

    try {
      Await.result(testSource.via(providers.join(task.flow())).runWith(returnSink), 6 seconds)
    } catch { case NonFatal(ex) => }

    resultToList(returnedResult) shouldEqual expectedReturnedResult
    resultToList(failoverResult) shouldEqual List("event6", "event3", "event4", "event5")
    resultToList(operatedResult) should (contain("event1") and contain("event2"))
    task.shutdown()

    failoverResult.forEach(println)
  }

}
