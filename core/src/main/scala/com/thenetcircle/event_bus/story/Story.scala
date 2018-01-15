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

case class StorySettings(name: String, initStatus: StoryStatus = StoryStatus.INIT)

class Story(val settings: StorySettings,
            val sourceTask: SourceTask,
            val sinkTask: SinkTask,
            val transformTasks: Option[List[TransformTask]] = None,
            val fallbackTasks: Option[List[SinkTask]] = None)
    extends SourceTask
    with StrictLogging {

  private var status: StoryStatus = settings.initStatus
  private def updateStatus(_status: StoryStatus): Unit = {
    status = _status
  }
  def getStatus(): StoryStatus = status

  private var graphId = 0
  private def decorateGraph(
      graph: Graph[FlowShape[Event, Event], NotUsed]
  ): Graph[FlowShape[Event, Event], NotUsed] = {
    graphId = graphId + 1
    Story.decorateGraph(graph, s"${settings.name}-$graphId", fallbackTasks)
  }

  override def getGraph(): Source[Event, NotUsed] =
    Source
      .fromGraph(
        GraphDSL
          .create() { implicit builder =>
            import GraphDSL.Implicits._

            // variables
            val source = sourceTask.getGraph()
            var transformations = transformTasks
              .map(_bList => {
                var _bChain = Flow[Event]
                _bList.foreach(_b => _bChain = _bChain.via(decorateGraph(_b.getGraph())))
                _bChain
              })
              .getOrElse(Flow[Event])
            // val sink = sinkTask.map(s => decorateGraph(s.getGraph())).getOrElse(Flow[Event])
            val sink = decorateGraph(sinkTask.getGraph())
            val confirmation = builder.add(decorateGraph(sourceTask.getCommittingGraph()))

            // workflow
            source ~> transformations ~> sink ~> confirmation

            // ports
            SourceShape(confirmation.out)
          }
      )
      .named(settings.name)

  override def getCommittingGraph(): Flow[Event, Event, NotUsed] = Flow[Event]

  def run()(implicit runningContext: TaskRunningContext): NotUsed = {
    implicit val materializer: Materializer = runningContext.getMaterializer()
    getGraph().runWith(Sink.ignore.mapMaterializedValue(m => NotUsed))
  }
}

object Story extends StrictLogging {

  def decorateGraph(
      graph: Graph[FlowShape[Event, Event], NotUsed],
      graphId: String,
      fallbackTasks: Option[List[SinkTask]] = None
  ): Graph[FlowShape[Event, Event], NotUsed] = {

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
                (if (fallbackTasks.isDefined) " Sending to fallbackTasks." else "")
            logger.debug(logMessage)
            _event
          })
          // TODO: use nested fallback graphs
          fallbackTasks.foreach(acList => failedGraph = failedGraph.via(acList.head.getGraph()))

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
