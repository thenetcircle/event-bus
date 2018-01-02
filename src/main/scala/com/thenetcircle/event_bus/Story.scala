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

package com.thenetcircle.event_bus

import akka.NotUsed
import akka.stream.SourceShape
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, Source}
import com.thenetcircle.event_bus.StoryStatus.StoryStatus
import com.thenetcircle.event_bus.interface._
import com.typesafe.config.Config

class Story(settings: StorySettings,
            source: ISource,
            sink: ISink,
            operations: List[IOperation] = List.empty,
            fallbacks: List[ISink] = List.empty,
            initStatus: StoryStatus = StoryStatus.INIT)
    extends ISource {

  private var status: StoryStatus = initStatus

  def updateStatus(_status: StoryStatus): Unit = {
    status = _status
  }

  override def outputGraph = Source.fromGraph(
    GraphDSL
      .create() { implicit builder =>
        import GraphDSL.Implicits._

        val ackShape = builder.add(source.ackGraph)

        // format: off

        source.outputGraph ~> sink.inputGraph ~> ackShape.in

        // format: on

        SourceShape(ackShape.out)
      }
      .named(settings.name)
  )

  override def ackGraph = Flow[Event]

  def start(): NotUsed = outputGraph.runWith(Sink.ignore.mapMaterializedValue(m => NotUsed))
}

object Story {

  def apply(config: Config): Story = ???

}

case class StorySettings(name: String)

object StoryStatus extends Enumeration {
  type StoryStatus = Value

  val INIT = Value(1, "INIT")
  val DEPLOYING = Value(2, "DEPLOYING")
  val RUNNING = Value(3, "RUNNING")
  val FAILED = Value(4, "FAILED")
  val STOPPING = Value(5, "STOPPING")
  val STOPPED = Value(6, "STOPPED")
}
