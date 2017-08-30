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
import com.thenetcircle.event_bus.transporter.entrypoint.{
  EntryPoint,
  EntryPointPriority,
  EntryPointSettings,
  HttpEntryPointSettings
}
import com.thenetcircle.event_bus.{Event, EventFormat, EventPriority}
import com.typesafe.config.ConfigFactory

class TransporterSpec extends AkkaTestSpec {

  behavior of "Transporter"

  it should "be delivered according to the priority" in {
    val testSource = TestPublisher.probe[Event]()
    val testSink   = TestSubscriber.probe[Event]()

    val transporter = getTransporter(Vector(Source.fromPublisher(testSource)),
                                     Sink.fromSubscriber(testSink))
    transporter.run()

    val testEvent1 = createTestEvent(priority = EventPriority.Urgent)
    val testEvent2 = createTestEvent(priority = EventPriority.High)
    val testEvent3 = createTestEvent(priority = EventPriority.Medium)
    val testEvent4 = createTestEvent(priority = EventPriority.Normal)
    val testEvent5 = createTestEvent(priority = EventPriority.Low)

    testSource.sendNext(testEvent5)
    testSource.sendNext(testEvent3)
    testSource.sendNext(testEvent2)
    testSource.sendNext(testEvent4)
    testSource.sendNext(testEvent1)

    testSink.request(5)
    testSink.expectNext(testEvent1)
    testSink.expectNext(testEvent2)
    testSink.expectNext(testEvent3)
    testSink.expectNext(testEvent4)
    testSink.expectNext(testEvent5)
  }

  private def getTransporter(sources: Vector[Source[Event, NotUsed]],
                             committer: Sink[Event, NotUsed]): Transporter = {
    val entryPointSettings = HttpEntryPointSettings(
      "TestHttpEntryPoint",
      EntryPointPriority.Normal,
      100,
      10,
      EventFormat.DefaultFormat,
      "localhost",
      8888
    )

    val testEntryPoints = sources.map(s =>
      new EntryPoint {
        override val settings: EntryPointSettings = entryPointSettings
        override def port: Source[Event, _]       = s
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
      None
    )

    new Transporter(settings,
                    testEntryPoints,
                    () => testPipelineLeftPort,
                    committer)
  }

}
