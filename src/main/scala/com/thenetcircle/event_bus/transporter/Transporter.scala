package com.thenetcircle.event_bus.transporter
import akka.NotUsed
import akka.stream.scaladsl.{ GraphDSL, MergePreferred, RunnableGraph }
import akka.stream.{ ClosedShape, Graph, SinkShape, SourceShape }
import com.thenetcircle.event_bus.Event

case class TransporterSettings(name: String)

class Transporter(
    entryPoints: Set[Graph[SourceShape[Event], NotUsed]],
    pipelineIn: Graph[SinkShape[Event], NotUsed],
    settings: TransporterSettings
) {

  lazy val stream: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL
      .create() { implicit builder =>
        import GraphDSL.Implicits._

        val merge = builder.add(MergePreferred[Event](entryPoints.size))

        var i = 0
        entryPoints foreach { ep =>
          // TODO high priority and fallback events goes to preferred channel
          ep ~> merge.in(i)
          i = i + 1
        }

        merge.out ~> pipelineIn

        ClosedShape
      }
      .named(settings.name)
  )

  def run(): Unit = stream.run()

}
