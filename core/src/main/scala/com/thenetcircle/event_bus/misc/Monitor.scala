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
import com.thenetcircle.event_bus.event.EventStatus._
import com.thenetcircle.event_bus.event.{Event, EventStatus}
import kamon.Kamon
import kamon.metric.instrument.{Counter, InstrumentFactory}
import kamon.metric.{EntityRecorderFactory, GenericEntityRecorder}

import scala.collection.mutable

object Monitor {
  private var isKamonEnabled: Boolean  = false
  private var isSentryEnabled: Boolean = false

  def init()(implicit appContext: AppContext): Unit = {
    val config = appContext.getSystemConfig()
    isKamonEnabled = config.getBoolean("app.monitor.kamon.enable")
    isSentryEnabled = config.getBoolean("app.monitor.sentry.enable")

    if (isKamonEnabled) {
      Kamon.start()
      appContext.addShutdownHook(Kamon.shutdown())
    }

    if (isSentryEnabled) {
      import io.sentry.Sentry
      val dsn = config.getString("app.monitor.sentry.dsn")
      if (dsn.nonEmpty) {

        val release      = BuildInfo.version
        val environment  = appContext.getAppEnv()
        val tags         = s"app_name:${appContext.getAppName()}"
        val app_packages = "com.thenetcircle.event_bus"

        Sentry.init(
          s"$dsn?release=$release&environment=$environment&tags=$tags&stacktrace.app.packages=$app_packages"
        )

      }
    }
  }

  def isEnabled(): Boolean = isKamonEnabled

}

trait Monitoring {

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
