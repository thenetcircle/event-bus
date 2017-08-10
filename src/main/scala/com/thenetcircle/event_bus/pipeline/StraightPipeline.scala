package com.thenetcircle.event_bus.pipeline

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{ BroadcastHub, Keep, MergeHub, Sink, Source }

case class StraightPipelineSettings(
    name: String = "DefaultStraightPipeline"
) extends PipelineSettings {
  def withName(name: String): StraightPipelineSettings = copy(name = name)
}

class StraightPipeline(pipelineSettings: StraightPipelineSettings)(implicit system: ActorSystem,
                                                                   materializer: Materializer)
    extends Pipeline(pipelineSettings) {

  private val (sink, source) =
    MergeHub
      .source[In](perProducerBufferSize = 16)
      .toMat(BroadcastHub.sink[Out](bufferSize = 256))(Keep.both)
      .run()

  def inlet(): Sink[In, NotUsed] = sink.named(s"$pipelineName-inlet-${inletId.getAndIncrement()}")

  def outlet(): Source[Out, NotUsed] = source.named(s"$pipelineName-outlet-${outletId.getAndIncrement()}")

}

object StraightPipeline {

  def apply(pipelineSettings: StraightPipelineSettings)(implicit system: ActorSystem,
                                                        materializer: Materializer): StraightPipeline =
    new StraightPipeline(pipelineSettings)

}
