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
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import com.thenetcircle.event_bus.dispatcher.endpoint.{
  EndPoint,
  EndPointSettings,
  EndPointType
}
import com.thenetcircle.event_bus.pipeline.PipelineType.PipelineType
import com.thenetcircle.event_bus.pipeline._
import com.thenetcircle.event_bus.testkit.AkkaStreamSpec
import com.thenetcircle.event_bus.{Event, createFlowFromSink, createTestEvent}
import com.typesafe.config.ConfigFactory

class DispatcherSpec extends AkkaStreamSpec {

  behavior of "Dispatcher"

  it should "be properly delivered each port of endpoint and committer" in {

    val testSource1 = TestPublisher.probe[Event]()
    val testSource2 = TestPublisher.probe[Event]()
    val testSource3 = TestPublisher.probe[Event]()

    val testSink1 = TestSubscriber.probe[Event]()
    val testSink2 = TestSubscriber.probe[Event]()
    val testSink3 = TestSubscriber.probe[Event]()

    val testCommitter = TestSubscriber.probe[Event]()

    val pipeline = createPipeline(
      Source[Source[Event, NotUsed]](
        Source.fromPublisher(testSource1) :: Source
          .fromPublisher(testSource2) :: Source
          .fromPublisher(testSource3) :: Nil),
      Sink.fromSubscriber(testCommitter)
    )

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

    val dispatcher = createDispatcher(Vector(endPoint), pipeline)

    dispatcher.run()

    val testEvent1 = createTestEvent(name = "TestEvent1")
    val testEvent2 = createTestEvent(name = "TestEvent2")
    val testEvent3 = createTestEvent(name = "TestEvent3")
    val testEvent4 = createTestEvent(name = "TestEvent4")

    testCommitter.request(3)
    testSink1.request(1)
    testSink2.request(1)
    testSink3.request(1)

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

  it should "evenly delivery to endpoints" in {

    val testSource1 = Source.fromIterator(
      () =>
        (for (i <- 1 to 10)
          yield createTestEvent(name = s"Source1-TestEvent$i")).iterator)

    val testSource2 = Source.fromIterator(
      () =>
        (for (i <- 1 to 10)
          yield createTestEvent(name = s"Source2-TestEvent$i")).iterator)

    val testSource3 = Source.fromIterator(
      () =>
        (for (i <- 1 to 10)
          yield createTestEvent(name = s"Source3-TestEvent$i")).iterator)

    val testSource4 = Source.fromIterator(
      () =>
        (for (i <- 1 to 10)
          yield createTestEvent(name = s"Source4-TestEvent$i")).iterator)

    val testSink1 = TestSubscriber.probe[Event]()
    val testSink2 = TestSubscriber.probe[Event]()
    val testSink3 = TestSubscriber.probe[Event]()
    val testSink4 = TestSubscriber.probe[Event]()
    val testSink5 = TestSubscriber.probe[Event]()

    val pipeline = createPipeline(
      Source[Source[Event, NotUsed]](
        testSource1 :: testSource2 :: testSource3 :: testSource4 :: Nil),
      Flow[Event].to(Sink.ignore)
    )

    val endPoints = Vector[EndPoint](
      new EndPoint {
        var currentIndex = 0
        override val settings: EndPointSettings = new EndPointSettings {
          override val name         = "TestEndPoint1"
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
      },
      new EndPoint {
        override val settings: EndPointSettings = new EndPointSettings {
          override val name         = "TestEndPoint2"
          override val endPointType = EndPointType.HTTP
        }
        override def stream: Flow[Event, Event, NotUsed] =
          createFlowFromSink(Sink.fromSubscriber(testSink4))
      },
      new EndPoint {
        override val settings: EndPointSettings = new EndPointSettings {
          override val name         = "TestEndPoint3"
          override val endPointType = EndPointType.HTTP
        }
        override def stream: Flow[Event, Event, NotUsed] =
          createFlowFromSink(Sink.fromSubscriber(testSink5))
      }
    )

    val dispatcher = createDispatcher(endPoints, pipeline)
    dispatcher.run()

    for (i <- 1 to 10) {
      testSink1.requestNext().metadata.name shouldEqual s"Source1-TestEvent$i"
      testSink2.requestNext().metadata.name shouldEqual s"Source4-TestEvent$i"
      testSink4.requestNext().metadata.name shouldEqual s"Source2-TestEvent$i"
      testSink5.requestNext().metadata.name shouldEqual s"Source3-TestEvent$i"
    }

    testSink1.expectComplete()
    testSink2.expectComplete()
    testSink4.expectComplete()
    testSink5.expectComplete()
  }

  it should "process maximum specific sub-streams at a time" in {}

  private def createDispatcher(endPoints: Vector[EndPoint],
                               pipeline: Pipeline): Dispatcher = {

    val dispatcherSettings =
      DispatcherSettings(
        ConfigFactory.parseString("""
                                    |{
                                    |  name = TestDispatcher
                                    |  max-parallel-sources = 10
                                    |  endpoints = [{
                                    |    type = http
                                    |    name = TestDispatcher-TestEndPoint
                                    |    request.host = localhost
                                    |  }]
                                    |  pipeline {
                                    |    name = TestPipeline
                                    |    outlet {
                                    |      group-id = "TestDispatcher"
                                    |    }
                                    |  }
                                    |}
                                  """.stripMargin))

    new Dispatcher(dispatcherSettings, pipeline, endPoints)
  }

  private def createPipeline(
      outletStream: => Source[Source[Event, NotUsed], NotUsed],
      committer: => Sink[Event, NotUsed]): Pipeline = {
    val testPipeline = PipelinePool().getPipeline("TestPipeline").get
    new Pipeline {
      override val pipelineType: PipelineType = testPipeline.pipelineType
      override val pipelineSettings: PipelineSettings =
        testPipeline.pipelineSettings

      override def getNewInlet(
          pipelineInletSettings: PipelineInletSettings): PipelineInlet =
        testPipeline.getNewInlet(pipelineInletSettings)

      override def getNewOutlet(pipelineOutletSettings: PipelineOutletSettings)(
          implicit materializer: Materializer): PipelineOutlet =
        new PipelineOutlet {
          override val pipeline: Pipeline = testPipeline
          override val outletName: String = "TestOutlet"
          override val outletSettings: PipelineOutletSettings =
            new PipelineOutletSettings {}

          override val stream: Source[Source[Event, NotUsed], NotUsed] =
            outletStream
        }

      override def getCommitter(
          pipelineCommitterSettings: PipelineCommitterSettings)
        : Sink[Event, NotUsed] = committer
    }
  }
}
