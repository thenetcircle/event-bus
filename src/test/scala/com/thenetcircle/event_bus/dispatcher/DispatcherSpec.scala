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

package com.thenetcircle.event_bus.dispatcher

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import com.thenetcircle.event_bus.dispatcher.endpoint.{
  EndPoint,
  EndPointSettings,
  EndPointType
}
import com.thenetcircle.event_bus.pipeline.{
  Pipeline,
  PipelineOutlet,
  PipelineOutletSettings,
  PipelinePool
}
import com.thenetcircle.event_bus.testkit.AkkaStreamSpec
import com.thenetcircle.event_bus.{createFlowFromSink, createTestEvent}
import com.thenetcircle.event_bus.{Event, EventFormat}
import com.typesafe.config.ConfigFactory

class DispatcherSpec extends AkkaStreamSpec {

  behavior of "Dispatcher"

  it should "be properly delivered each port of endpoint and committer" in {

    /*val dispatcherSettings = DispatcherSettings(
      name = "TestDispatcher",
      maxParallelSources = 10,
      endPointSettings = createHttpEndPointSettings(),
      pipeline = KafkaPipelineFactory.getPipeline("TestPipeline"),
      rightPortSettings =
        KafkaPipelineFactory.getRightPortSettings(ConfigFactory.empty()),
      None
    )*/
    val dispatcherSettings =
      DispatcherSettings(
        ConfigFactory.parseString("""
            |{
            |  name = TestDispatcher
            |  max-parallel-sources = 10
            |  endpoint {
            |    type = http
            |    name = TestDispatcher-TestEndPoint
            |    request.host = localhost
            |  }
            |  pipeline {
            |    name = TestPipeline
            |    outlet-settings {
            |      group-id = "TestDispatcher"
            |    }
            |  }
            |}
          """.stripMargin))

    val testSource1 = TestPublisher.probe[Event]()
    val testSource2 = TestPublisher.probe[Event]()
    val testSource3 = TestPublisher.probe[Event]()

    val testSink1 = TestSubscriber.probe[Event]()
    val testSink2 = TestSubscriber.probe[Event]()
    val testSink3 = TestSubscriber.probe[Event]()

    val testCommitter = TestSubscriber.probe[Event]()

    val pipelineOutlet = new PipelineOutlet {
      override val pipeline: Pipeline =
        PipelinePool().getPipeline("TestPipeline").get
      override val outletName: String = "TestOutlet"
      override val outletSettings: PipelineOutletSettings =
        new PipelineOutletSettings {
          override val eventFormat: EventFormat = EventFormat.DefaultFormat
        }

      override val stream: Source[Source[Event, NotUsed], NotUsed] =
        Source[Source[Event, NotUsed]](
          Source.fromPublisher(testSource1) :: Source
            .fromPublisher(testSource2) :: Source
            .fromPublisher(testSource3) :: Nil)

      override val committer: Flow[Event, Event, NotUsed] =
        createFlowFromSink(Sink.fromSubscriber(testCommitter))
    }

    val endPoint = new EndPoint {
      var currentIndex = 0
      override val settings: EndPointSettings = new EndPointSettings {
        override val name         = "TestEndPoint"
        override val endPointType = EndPointType.HTTP
      }
      override def stream: Flow[Event, Event, NotUsed] = {
        currentIndex += 1
        currentIndex match {
          case 1 => createFlowFromSink(Sink.fromSubscriber(testSink1))
          case 2 => createFlowFromSink(Sink.fromSubscriber(testSink2))
          case 3 => createFlowFromSink(Sink.fromSubscriber(testSink3))
          case _ => throw new Exception("index error")
        }
      }
    }

    val dispatcher =
      new Dispatcher(dispatcherSettings, pipelineOutlet, endPoint)

    dispatcher.run()

    val testEvent1 = createTestEvent(name = "TestEvent1")
    val testEvent2 = createTestEvent(name = "TestEvent2")
    val testEvent3 = createTestEvent(name = "TestEvent3")
    val testEvent4 = createTestEvent(name = "TestEvent4")

    testSink1.request(1)
    testSink2.request(1)
    testSink3.request(1)
    testCommitter.request(3)

    testSource1.sendNext(testEvent1)
    testSource2.sendNext(testEvent2)
    testSource3.sendNext(testEvent3)

    testSink1.expectNext(testEvent1)
    testSink2.expectNext(testEvent2)
    testSink3.expectNext(testEvent3)

    testCommitter.expectNextUnordered(testEvent1, testEvent2, testEvent3)

    val testError = new Exception("TestError")

    testSource1.sendComplete()
    testSink1.expectComplete()
    testSource2.sendError(testError)
    testSink2.expectError(testError)
    testCommitter.expectError()

    // TODO: will testSource3 still be alive?
    testSource3.sendNext(testEvent4)
    testSink3.requestNext(testEvent4)
  }

  it should "process maximum specific sub-streams at a time" in {}
}
