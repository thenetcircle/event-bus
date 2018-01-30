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

package com.thenetcircle.event_bus.misc

import com.thenetcircle.event_bus.BuildInfo
import com.thenetcircle.event_bus.context.AppContext
import com.thenetcircle.event_bus.event.extractor.EventExtractingException
import com.thenetcircle.event_bus.interfaces.EventStatus.{Fail, InFB, Norm, ToFB}
import com.thenetcircle.event_bus.interfaces.{Event, EventStatus}
import kamon.Kamon
import kamon.metric.instrument.{Counter, InstrumentFactory}
import kamon.metric.{EntityRecorderFactory, GenericEntityRecorder}

import scala.collection.mutable

object Monitor {
  private var isKamonEnabled: Boolean  = false
  private var isSentryEnabled: Boolean = false

  def init()(implicit appContext: AppContext): Unit = {
    isKamonEnabled = appContext.getSystemConfig().getBoolean("app.monitor.kamon.enable")
    isSentryEnabled = appContext.getSystemConfig().getBoolean("app.monitor.sentry.enable")

    if (isKamonEnabled) {
      Kamon.start()
      appContext.addShutdownHook(Kamon.shutdown())
    }

    if (isSentryEnabled) {
      import io.sentry.Sentry

      val release     = BuildInfo.version
      val environment = appContext.getAppEnv()
      val tags        = s"app_name:${appContext.getAppName()}"

      Sentry.init(
        s"http://6d41dd5bb5c045aaa6b8f9ffd518e6c0:a62c806cf0cc4d4a85d96db9d337577e@thin.thenetcircle.lab:8080/2?release=$release&environment=$environment&tags=$tags&stacktrace.app.packages=com.thenetcircle.event_bus"
      )
    }
  }

  def isEnabled(): Boolean = isKamonEnabled

}

trait MonitoringHelp {

  def getStoryMonitor(storyName: String): StoryMonitor = StoryMonitor(storyName)

}

class StoryMonitor(storyName: String) {

  import StoryMonitor._

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
      case Norm => entity.foreach(_.normEvent.increment())
      case ToFB(exOp) =>
        entity.foreach(e => {
          e.toFBEvent.increment()
          exOp.foreach(e.exception(_).increment())
        })
      case InFB => entity.foreach(_.inFBEvent.increment())
      case Fail(ex: EventExtractingException) =>
        entity.foreach(e => {
          e.badFormatEvent.increment()
          e.exception(ex).increment()
        })
      case Fail(ex) =>
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
    val newEvent: Counter        = counter("new-event")
    val normEvent: Counter       = counter("processed.normal")
    val toFBEvent: Counter       = counter("processed.tofallback")
    val inFBEvent: Counter       = counter("processed.infallback")
    val failEvent: Counter       = counter("processed.failure")
    val badFormatEvent: Counter  = counter("processed.badformat")
    val completion: Counter      = counter("completion")
    val termination: Counter     = counter("termination")
    def exception(ex: Throwable) = counter("exceptions." + ex.getClass.getSimpleName)
    def error(ex: Throwable)     = counter("errors." + ex.getClass.getSimpleName)
  }

  object StoryMetrics extends EntityRecorderFactory[StoryMetrics] {
    def category: String                                                   = "story"
    def createRecorder(instrumentFactory: InstrumentFactory): StoryMetrics = new StoryMetrics(instrumentFactory)
  }
}
