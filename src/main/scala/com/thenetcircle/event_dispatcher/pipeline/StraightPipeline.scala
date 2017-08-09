package com.thenetcircle.event_dispatcher.pipeline

import java.util.concurrent.atomic.AtomicInteger

import akka.stream.scaladsl.{ BroadcastHub, Keep, MergeHub, Sink, Source }
import com.thenetcircle.event_dispatcher.Event

case class StraightPipelineSettings(
    name: String = "DefaultStraightPipeline"
) extends PipelineSettings {
  def withName(name: String): StraightPipelineSettings = copy(name = name)
}

class StraightPipeline(pipelineSettings: StraightPipelineSettings) {

  type In = Event
  type Out = Event

  private val (sink, source) =
    MergeHub
      .source[In](perProducerBufferSize = 16)
      .toMat(BroadcastHub.sink[Out](bufferSize = 256))(Keep.both)
      .run()

  private val pipelineName = pipelineSettings.name
  private val inletId = new AtomicInteger(0)
  private val outletId = new AtomicInteger(0)

  def inlet(): Sink[In, _] = sink.named(s"$pipelineName-inlet-${inletId.getAndIncrement()}")

  def outlet(): Source[Out, _] = source.named(s"$pipelineName-outlet-${outletId.getAndIncrement()}")

}
