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

package com.thenetcircle.event_bus.transporter

import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import com.thenetcircle.event_bus.pipeline.PipelineType.PipelineType
import com.thenetcircle.event_bus.pipeline.kafka.KafkaPipelineFactory
import com.thenetcircle.event_bus.pipeline._
import com.thenetcircle.event_bus.{Event, EventFormat}
import com.thenetcircle.event_bus.testkit.AkkaStreamSpec
import com.thenetcircle.event_bus.{createFlowFromSink, createTestEvent}
import com.thenetcircle.event_bus.transporter.entrypoint._
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointPriority.EntryPointPriority
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future

class TransporterSpec extends AkkaStreamSpec {

  behavior of "Transporter"

  it should "be delivered according to the priority of the EntryPoint" in {
    val eventsCount     = 20000
    val testLowEvent    = createTestEvent("testEvent1")
    val testNormalEvent = createTestEvent("testEvent2")
    val testHighEvent   = createTestEvent("testEvent3")

    val testPipelinePort = TestSubscriber.probe[Event]()

    val testLowSource1 =
      Source.fromIterator(() => Seq.fill(eventsCount)(testLowEvent).iterator)
    val testLowSource2 =
      Source.fromIterator(() => Seq.fill(eventsCount)(testLowEvent).iterator)

    val testNormalSource1 =
      Source.fromIterator(() => Seq.fill(eventsCount)(testNormalEvent).iterator)
    val testNormalSource2 =
      Source.fromIterator(() => Seq.fill(eventsCount)(testNormalEvent).iterator)

    val testHighSource =
      Source.fromIterator(() => Seq.fill(eventsCount)(testHighEvent).iterator)

    createTransporter(
      Vector(
        (EntryPointPriority.Low, testLowSource1),
        (EntryPointPriority.Low, testLowSource2),
        (EntryPointPriority.High, testHighSource),
        (EntryPointPriority.Normal, testNormalSource1),
        (EntryPointPriority.Normal, testNormalSource2)
      ),
      Vector(Sink.fromSubscriber(testPipelinePort))
    ).run()

    var collected = Seq.empty[Event]
    for (_ <- 1 to eventsCount) {
      testPipelinePort.request(1)
      collected :+= testPipelinePort.expectNext()
    }

    val lows    = collected.count(_ == testLowEvent).toDouble
    val normals = collected.count(_ == testNormalEvent).toDouble
    val highs   = collected.count(_ == testHighEvent).toDouble

    (highs / lows).round shouldEqual 6
    (highs / normals).round shouldEqual 2
    (normals / lows).round shouldEqual 3
  }

  it should "commit event after transported" in {
    val testPublisher = TestPublisher.probe[Event]()

    createTransporter(
      Vector(
        (EntryPointPriority.Normal, Source.fromPublisher(testPublisher))
      ),
      Vector(Sink.ignore),
      commitParallelism = 10
    ).run()

    var result = new AtomicInteger(0)

    val testEvent =
      createTestEvent("TestEvent").withCommitter(() =>
        Future { result.incrementAndGet() })
    val count = 10000

    for (_ <- 1 to count) {
      testPublisher.sendNext(testEvent)
    }

    Thread.sleep(100)
    result.get() shouldEqual count
  }

  it should "concurrently processing when transportParallelism greater than 1" in {
    var result = new AtomicInteger(0)
    val testEvent = createTestEvent("TestEvent").withCommitter(() =>
      Future { result.incrementAndGet() })

    val testCount = 10000
    val testSource1 =
      Source.fromIterator(() => Seq.fill(testCount)(testEvent).iterator)
    val testSource2 =
      Source.fromIterator(() => Seq.fill(testCount)(testEvent).iterator)
    val testSource3 =
      Source.fromIterator(() => Seq.fill(testCount)(testEvent).iterator)

    val testSink1 = TestSubscriber.probe[Event]()
    val testSink2 = TestSubscriber.probe[Event]()

    createTransporter(
      Vector(
        (EntryPointPriority.High, testSource1),
        (EntryPointPriority.Normal, testSource2),
        (EntryPointPriority.Low, testSource3)
      ),
      Vector(Sink.fromSubscriber(testSink1), Sink.fromSubscriber(testSink2)),
      commitParallelism = 10,
      transportParallelism = 2
    ).run()

    var sink1Collected = Seq.empty[Event]
    var sink2Collected = Seq.empty[Event]
    for (_ <- 1 to (testCount / 2) * 3) {
      sink1Collected :+= testSink1.requestNext()
      sink2Collected :+= testSink2.requestNext()
    }

    sink1Collected.size shouldEqual sink2Collected.size

    Thread.sleep(100)
    result.get() shouldEqual 30000
  }

  def createTransporter(
      testSources: Vector[(EntryPointPriority, Source[Event, _])],
      testPipelinePort: Vector[Sink[Event, _]],
      commitParallelism: Int = 1,
      transportParallelism: Int = 1)(
      implicit system: ActorSystem,
      materializer: Materializer): Transporter = {
    val entryPointSettings = HttpEntryPointSettings(
      "TestHttpEntryPoint",
      EntryPointPriority.Normal,
      100,
      10,
      EventFormat.DefaultFormat,
      ServerSettings(system),
      "localhost",
      8888
    )

    val testEntryPoints = testSources.map(_source =>
      new EntryPoint {
        override val settings: EntryPointSettings = new EntryPointSettings {
          override val name           = s"TestEntryPoint-${_source._1}"
          override val priority       = _source._1
          override val entryPointType = EntryPointType.HTTP
          override val eventFormat    = EventFormat.DefaultFormat
        }

        override def stream: Source[Event, _] = _source._2
    })

    val settings = TransporterSettings(
      "TestTransporter",
      commitParallelism,
      transportParallelism,
      Vector(entryPointSettings),
      PipelinePool().getPipeline("TestPipeline").get,
      KafkaPipelineFactory().createPipelineInletSettings(ConfigFactory.empty()),
      None
    )

    var currentPipelinePortIndex = 0
    val testPipeline             = PipelinePool().getPipeline("TestPipeline").get
    val pipeline = new Pipeline {
      override val pipelineType: PipelineType = testPipeline.pipelineType
      override val pipelineSettings: PipelineSettings =
        testPipeline.pipelineSettings
      override def getNewOutlet(pipelineOutletSettings: PipelineOutletSettings)(
          implicit materializer: Materializer): PipelineOutlet =
        testPipeline.getNewOutlet(pipelineOutletSettings)
      override def getCommitter(
          pipelineCommitterSettings: PipelineCommitterSettings)
        : Sink[Event, NotUsed] =
        testPipeline.getCommitter(pipelineCommitterSettings)

      override def getNewInlet(
          pipelineInletSettings: PipelineInletSettings): PipelineInlet = {
        val testPP = testPipelinePort(currentPipelinePortIndex)
        currentPipelinePortIndex += 1
        new PipelineInlet {
          override val pipeline: Pipeline = testPipeline
          override val inletName: String  = "TestPipelineInlet"
          override val inletSettings: PipelineInletSettings =
            new PipelineInletSettings {}
          override val stream: Flow[Event, Event, NotUsed] =
            createFlowFromSink(testPP)
        }
      }
    }
    new Transporter(
      settings,
      testEntryPoints,
      pipeline
    )
  }

}
