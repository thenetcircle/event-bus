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
import com.thenetcircle.event_bus.interface._
import com.thenetcircle.event_bus.story.StoryStatus.StoryStatus
import com.typesafe.scalalogging.StrictLogging

case class StorySettings(name: String)

class Story(settings: StorySettings,
            sourcePlot: SourcePlot,
            sinkPlot: SinkPlot,
            opPlots: List[OpPlot] = List.empty,
            fallback: Option[SinkPlot] = None,
            initStatus: StoryStatus = StoryStatus.INIT)
    extends SourcePlot
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

  override def getGraph(): Source[Event, NotUsed] =
    Source
      .fromGraph(
        GraphDSL
          .create() { implicit builder =>
            import GraphDSL.Implicits._

            // variables
            val source = sourcePlot.getGraph()
            var operations = Flow[Event]
            opPlots.foreach(op => operations = operations.via(decorateGraph(op.getGraph())))
            val sink = decorateGraph(sinkPlot.getGraph())
            val committing = builder.add(decorateGraph(sourcePlot.getCommittingGraph()))

            // workflow
            source ~> operations ~> sink ~> committing

            // ports
            SourceShape(committing.out)
          }
      )
      .named(storyName)

  override def getCommittingGraph(): Flow[Event, Event, NotUsed] = Flow[Event]

  def start()(implicit materializer: Materializer): NotUsed =
    getGraph().runWith(Sink.ignore.mapMaterializedValue(m => NotUsed))
}

object Story extends StrictLogging {

  def decorateGraph(graph: Graph[FlowShape[Event, Event], NotUsed],
                    graphId: String,
                    fallback: Option[SinkPlot] = None): Graph[FlowShape[Event, Event], NotUsed] = {

    Flow.fromGraph(
      GraphDSL
        .create() { implicit builder =>
          import GraphDSL.Implicits._

          // variables
          val mainGraph = builder.add(graph)
          val checkGraph = builder.add(new Partition[Event](2, e => if (e.isFailed) 1 else 0))
          var failedGraph = Flow[Event].map(_event => {
            val logMessage =
              s"Event ${_event.uniqueName} was processing failed on graph: $graphId." +
                (if (fallback.isDefined) " Sending to fallback." else "")
            logger.debug(logMessage)
            _event
          })
          fallback.foreach(f => failedGraph = failedGraph.via(f.getGraph()))

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

object StoryStatus extends Enumeration {
  type StoryStatus = Value

  val INIT = Value(1, "INIT")
  val DEPLOYING = Value(2, "DEPLOYING")
  val RUNNING = Value(3, "RUNNING")
  val FAILED = Value(4, "FAILED")
  val STOPPING = Value(5, "STOPPING")
  val STOPPED = Value(6, "STOPPED")
}
