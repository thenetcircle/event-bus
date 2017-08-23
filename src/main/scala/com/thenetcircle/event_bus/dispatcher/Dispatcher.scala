package com.thenetcircle.event_bus.dispatcher
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, Materializer}
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.dispatcher.endpoint.EndPoint
import com.thenetcircle.event_bus.pipeline.Pipeline.RightPort
import com.thenetcircle.event_bus.pipeline.PipelineFactory

class Dispatcher(
    settings: DispatcherSettings,
    pipelineRightPort: RightPort,
    endPoints: Vector[EndPoint])(implicit materializer: Materializer) {

  // TODO: draw a graph in comments
  // TODO: error handler
  // TODO: parallel and async
  lazy val stream: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL
      .create() { implicit builder =>
        import GraphDSL.Implicits._

        // TODO: use NotUsed
        val source: Source[Event, _] =
          pipelineRightPort.port.flatMapMerge(settings.maxParallelSources,
                                              identity)

        val balanceShape =
          builder.add(Balance[Event](endPoints.size))

        // format: off
        
        source ~> balanceShape.in

        for (i <- endPoints.indices) {
          val committer = pipelineRightPort.committer.to(Sink.ignore)

                  balanceShape.out(i) ~> endPoints(i).port ~> committer

        }

        // format: on

        ClosedShape
      }
      .named(settings.name)
  )

  // TODO add a transporter controller as a materialized value
  def run(): Unit = stream.run()

}

object Dispatcher {
  def apply(settings: DispatcherSettings)(
      implicit system: ActorSystem): Dispatcher = {

    implicit val materializer = settings.materializerSettings match {
      case Some(_settings) => ActorMaterializer(_settings)
      case None            => ActorMaterializer()
    }

    val pipelineRightPort = PipelineFactory.getRightPort(
      settings.pipelineName,
      settings.pipelineRightPortConfig)

    val endPoints = settings.endPointsSettings.map(EndPoint(_))

    new Dispatcher(settings, pipelineRightPort, endPoints)
  }
}
