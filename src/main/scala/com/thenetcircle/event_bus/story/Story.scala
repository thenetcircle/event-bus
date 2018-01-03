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
import akka.stream.scaladsl.{Flow, GraphDSL, Partition, Sink, Source}
import akka.stream.{FlowShape, Graph, Materializer, SourceShape}
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.story.interface._
import com.thenetcircle.event_bus.story.StoryStatus.StoryStatus
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

class Story(settings: StorySettings,
            source: ISource,
            sink: ISink,
            operations: List[IOperation] = List.empty,
            fallback: Option[ISink] = None,
            initStatus: StoryStatus = StoryStatus.INIT)
    extends ISource
    with StrictLogging {

  val storyName: String = settings.name

  private var status: StoryStatus = initStatus
  private def updateStatus(_status: StoryStatus): Unit = {
    status = _status
  }
  def getStatus(): StoryStatus = status

  private var graphId = 0
  private def decorateGraph(
      graph: Graph[FlowShape[Event, Event], NotUsed]
  ): Graph[FlowShape[Event, Event], NotUsed] = {
    graphId = graphId + 1
    Story.decorateGraph(graph, s"$storyName-$graphId", fallback)
  }

  override val graph = Source
    .fromGraph(
      GraphDSL
        .create() { implicit builder =>
          import GraphDSL.Implicits._

          // variables
          val sourceGraph     = source.graph
          var operationGraphs = Flow[Event]
          operations.foreach(op => operationGraphs = operationGraphs.via(decorateGraph(op.graph)))
          val sinkGraph = decorateGraph(sink.graph)
          val ackGraph  = builder.add(decorateGraph(source.ackGraph))

          // workflow
          sourceGraph ~> operationGraphs ~> sinkGraph ~> ackGraph.in

          // ports
          SourceShape(ackGraph.out)
        }
    )
    .named(storyName)

  override val ackGraph = Flow[Event]

  def start()(implicit materializer: Materializer): NotUsed =
    graph.runWith(Sink.ignore.mapMaterializedValue(m => NotUsed))
}

object Story extends StrictLogging {

  def apply(config: Config): Story = ???

  def decorateGraph(graph: Graph[FlowShape[Event, Event], NotUsed],
                    graphId: String,
                    fallback: Option[ISink] = None): Graph[FlowShape[Event, Event], NotUsed] = {

    Flow.fromGraph(
      GraphDSL
        .create() { implicit builder =>
          import GraphDSL.Implicits._

          // variables
          val mainGraph  = builder.add(graph)
          val checkGraph = builder.add(new Partition[Event](2, e => if (e.isFailed()) 1 else 0))
          var failedGraph = Flow[Event].map(_event => {
            val logMessage =
              s"Event ${_event.uniqueName} was processing failed on graph: $graphId." +
                (if (fallback.isDefined) " Sending to fallback." else "")
            logger.debug(logMessage)
            _event
          })
          fallback.foreach(f => failedGraph = failedGraph.via(f.graph))

          // workflow
          // format: off
          mainGraph ~> checkGraph.in
                       checkGraph.out(1) ~> failedGraph
          // format: on

          // ports
          FlowShape(mainGraph.in, checkGraph.out(0))
        }
    )
  }

}

case class StorySettings(name: String)

object StoryStatus extends Enumeration {
  type StoryStatus = Value

  val INIT      = Value(1, "INIT")
  val DEPLOYING = Value(2, "DEPLOYING")
  val RUNNING   = Value(3, "RUNNING")
  val FAILED    = Value(4, "FAILED")
  val STOPPING  = Value(5, "STOPPING")
  val STOPPED   = Value(6, "STOPPED")
}
