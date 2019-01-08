package com.thenetcircle.event_bus.story

import com.thenetcircle.event_bus.event.EventStatus._
import com.thenetcircle.event_bus.event.extractor.EventExtractingException
import com.thenetcircle.event_bus.event.{Event, EventStatus}
import com.thenetcircle.event_bus.misc.Monitor
import com.thenetcircle.event_bus.story.StoryMonitor.StoryMetrics
import kamon.Kamon
import kamon.metric.instrument.{Counter, InstrumentFactory}
import kamon.metric.{EntityRecorderFactory, GenericEntityRecorder}

import scala.collection.mutable

class StoryMonitor(storyName: String) {

  val entity: Option[StoryMetrics] =
    if (Monitor.isEnabled()) Some(Kamon.metrics.entity(StoryMetrics, storyName)) else None

  def newEvent(event: Event): StoryMonitor = {
    entity.foreach(_.newEvent.increment())
    this
  }

  def onCompleted(): StoryMonitor = {
    entity.foreach(_.completion.increment())
    this
  }

  def onTerminated(ex: Throwable): StoryMonitor = {
    entity.foreach(e => {
      e.termination.increment()
      e.error(ex).increment()
    })
    this
  }

  def onProcessed(status: EventStatus, event: Event): StoryMonitor = {
    status match {
      case NORMAL => entity.foreach(_.normalEvent.increment())
      case STAGING(exOp, _) =>
        entity.foreach(e => {
          e.stagingEvent.increment()
          exOp.foreach(e.exception(_).increment())
        })
      case STAGED   => entity.foreach(_.stagedEvent.increment())
      case SKIPPING => entity.foreach(_.skippingEvent.increment())
      case FAILED(ex: EventExtractingException, _) =>
        entity.foreach(e => {
          e.badFormatEvent.increment()
          e.exception(ex).increment()
        })
      case FAILED(ex, _) =>
        entity.foreach(e => {
          e.failedEvent.increment()
          e.exception(ex).increment()
        })
    }
    this
  }

}

object StoryMonitor {
  // TODO thread safe?
  private val instances = mutable.Map.empty[String, StoryMonitor]

  def apply(storyName: String): StoryMonitor =
    instances.getOrElse(storyName, {
      val newInstance = new StoryMonitor(storyName)
      instances += (storyName -> newInstance)
      newInstance
    })

  class StoryMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
    val newEvent: Counter       = counter("new-event")
    val normalEvent: Counter    = counter("processed.normal")
    val stagingEvent: Counter   = counter("processed.staging")
    val skippingEvent: Counter  = counter("processed.skipping")
    val stagedEvent: Counter    = counter("processed.staged")
    val failedEvent: Counter    = counter("processed.failed")
    val badFormatEvent: Counter = counter("processed.badformat")
    val completion: Counter     = counter("completion")
    val termination: Counter    = counter("termination")

    def exception(ex: Throwable) = try { counter("exceptions." + ex.getClass.getSimpleName) } catch {
      case _: Throwable => counter("exceptions.unknown")
    }

    def error(ex: Throwable) = try { counter("errors." + ex.getClass.getSimpleName) } catch {
      case _: Throwable => counter("exceptions.unknown")
    }
  }

  object StoryMetrics extends EntityRecorderFactory[StoryMetrics] {
    def category: String = "story"

    def createRecorder(instrumentFactory: InstrumentFactory): StoryMetrics = new StoryMetrics(instrumentFactory)
  }

}
