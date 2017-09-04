package com.thenetcircle.event_bus.dispatcher
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, MergeHub, RunnableGraph, Sink}
import akka.stream.{ActorMaterializer, Materializer}
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.dispatcher.endpoint.EndPoint
import com.thenetcircle.event_bus.pipeline.Pipeline.RightPort
import com.thenetcircle.event_bus.pipeline.PipelineFactory

class Dispatcher(settings: DispatcherSettings,
                 pipelineRightPort: RightPort,
                 endPoint: EndPoint)(implicit materializer: Materializer) {

  val committer: Sink[Event, NotUsed] =
    MergeHub
      .source[Event](perProducerBufferSize = 16)
      .to(pipelineRightPort.committer.to(Sink.ignore))
      .run()

  val endPointPort = endPoint.port

  // TODO: draw a graph in comments
  // TODO: error handler
  // TODO: parallel and async
  // TODO: Mat value
  lazy val stream: RunnableGraph[_] =
    pipelineRightPort.port
      .flatMapMerge(settings.maxParallelSources,
                    source => source.viaMat(endPointPort)(Keep.left))
      .via(pipelineRightPort.committer.async)
      .to(Sink.ignore)

  // TODO add a transporter controller as a materialized value
  def run(): Unit = stream.run()

}

object Dispatcher {
  def apply(settings: DispatcherSettings)(
      implicit system: ActorSystem): Dispatcher = {

    implicit val materializer =
      ActorMaterializer(settings.materializerSettings, Some(settings.name))

    val pipelineRightPort = PipelineFactory.getRightPort(
      settings.pipelineName,
      settings.pipelineRightPortConfig)

    val endPoint = EndPoint(settings.endPointSettings)

    new Dispatcher(settings, pipelineRightPort, endPoint)
  }
}
