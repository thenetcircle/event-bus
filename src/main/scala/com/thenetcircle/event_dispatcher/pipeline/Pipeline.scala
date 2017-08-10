package com.thenetcircle.event_dispatcher.pipeline
import java.util.concurrent.atomic.AtomicInteger

import com.thenetcircle.event_dispatcher.Event

trait PipelineSettings {
  def name: String
  def withName(name: String): PipelineSettings
}

abstract class Pipeline(pipelineSettings: PipelineSettings) {

  type In = Event
  type Out = Event

  protected val pipelineName: String = pipelineSettings.name
  protected val inletId = new AtomicInteger(0)
  protected val outletId = new AtomicInteger(0)

}
