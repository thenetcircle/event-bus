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

class Story(val settings: StorySettings,
            val source: ISource,
            val ops: Option[List[IOp]] = None,
            val sink: Option[ISink] = None,
            val fallback: Option[ISink] = None,
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

  override def getGraph(): Source[Event, NotUsed] =
    Source
      .fromGraph(
        GraphDSL
          .create() { implicit builder =>
            import GraphDSL.Implicits._

            // variables
            val _source = source.getGraph()
            val _committing = builder.add(decorateGraph(source.getCommittingGraph()))
            var _ops = ops
              .map(_opList => {
                var _chain = Flow[Event]
                _opList.foreach(_op => _chain = _chain.via(decorateGraph(_op.getGraph())))
                _chain
              })
              .getOrElse(Flow[Event])
            val _sink = sink.map(s => decorateGraph(s.getGraph())).getOrElse(Flow[Event])

            // workflow
            _source ~> _ops ~> _sink ~> _committing

            // ports
            SourceShape(_committing.out)
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
                    fallback: Option[ISink] = None): Graph[FlowShape[Event, Event], NotUsed] = {

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
