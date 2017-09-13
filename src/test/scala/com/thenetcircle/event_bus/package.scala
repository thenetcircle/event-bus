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

package com.thenetcircle
import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Sink}
import akka.util.ByteString

package object event_bus {
  def createTestEvent(name: String = "TestEvent",
                      time: Long = 111,
                      sourceType: EventSourceType = EventSourceType.Http,
                      body: String = "body",
                      format: EventFormat = EventFormat.DefaultFormat): Event =
    Event(
      EventMetaData("uuid", name, time, "publisher", ("user", "222")),
      EventBody(ByteString(body), format),
      "channel",
      sourceType
    )

  def createFlowFromSink(sink: Sink[Event, _]): Flow[Event, Event, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val inlet     = builder.add(Flow[Event])
      val outlet    = builder.add(Flow[Event])
      val broadcast = builder.add(Broadcast[Event](2))

      // format: off
      inlet ~> broadcast
      broadcast.out(0) ~> sink
      broadcast.out(1) ~> outlet
      // format: on

      FlowShape(inlet.in, outlet.out)
    })
}
