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
      case NORM => entity.foreach(_.normEvent.increment())
      case TOFB(exOp, _) =>
        entity.foreach(e => {
          e.toFBEvent.increment()
          exOp.foreach(e.exception(_).increment())
        })
      case INFB => entity.foreach(_.inFBEvent.increment())
      case SKIP => entity.foreach(_.skipEvent.increment())
      case FAIL(ex: EventExtractingException, _) =>
        entity.foreach(e => {
          e.badFormatEvent.increment()
          e.exception(ex).increment()
        })
      case FAIL(ex, _) =>
        entity.foreach(e => {
          e.failEvent.increment()
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
    val normEvent: Counter      = counter("processed.normal")
    val toFBEvent: Counter      = counter("processed.tofallback")
    val inFBEvent: Counter      = counter("processed.infallback")
    val failEvent: Counter      = counter("processed.failure")
    val skipEvent: Counter      = counter("processed.skip")
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
