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
import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import com.thenetcircle.event_bus.base.{AkkaTestSpec, createTestEvent}
import com.thenetcircle.event_bus.pipeline.Pipeline.LeftPort
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPointPriority.EntryPointPriority
import com.thenetcircle.event_bus.transporter.entrypoint.{
  EntryPoint,
  EntryPointPriority,
  HttpEntryPointSettings
}
import com.thenetcircle.event_bus.{Event, EventFormat}
import com.typesafe.config.ConfigFactory

class TransporterSpec extends AkkaTestSpec {

  behavior of "Transporter"

  it should "be delivered according to the priority of the EntryPoint" in {
    val testSource1 = TestPublisher.probe[Event]()
    val testSource2 = TestPublisher.probe[Event]()
    val testSource3 = TestPublisher.probe[Event]()
    val testSink    = TestSubscriber.probe[Event]()

    val transporter = getTransporter(
      Vector(
        (EntryPointPriority.High, Source.fromPublisher(testSource1)),
        (EntryPointPriority.Normal, Source.fromPublisher(testSource1)),
        (EntryPointPriority.Low, Source.fromPublisher(testSource1))
      ),
      Sink.fromSubscriber(testSink)
    )
    transporter.run()

    val testEvent1 = createTestEvent("testEvent1")
    val testEvent2 = createTestEvent("testEvent2")
    val testEvent3 = createTestEvent("testEvent3")

    testSource1.sendNext(testEvent1)
    testSource2.sendNext(testEvent2)
    testSource3.sendNext(testEvent3)

    println(testSink.expectNext())
  }

  private def getTransporter(
      testSources: Vector[(EntryPointPriority, Source[Event, NotUsed])],
      testSink: Sink[Event, NotUsed]): Transporter = {
    val entryPointSettings = HttpEntryPointSettings(
      "TestHttpEntryPoint",
      EntryPointPriority.Normal,
      100,
      10,
      EventFormat.DefaultFormat,
      "localhost",
      8888
    )

    val testEntryPoints = testSources.map(_source =>
      new EntryPoint {
        override val name: String                 = s"TestEntryPoint-${_source._1}"
        override val eventFormat: EventFormat     = EventFormat.DefaultFormat
        override val priority: EntryPointPriority = _source._1

        override def port: Source[Event, _] = _source._2
    })

    val testPipelineLeftPort = new LeftPort {
      override def port: Flow[Event, Event, NotUsed] = Flow[Event].map(e => e)
    }

    val settings = TransporterSettings(
      "TestTransporter",
      Vector(entryPointSettings),
      "TestPipeline",
      ConfigFactory.empty(),
      1,
      1,
      None
    )

    new Transporter(settings,
                    testEntryPoints,
                    () => testPipelineLeftPort,
                    testSink)
  }

}
