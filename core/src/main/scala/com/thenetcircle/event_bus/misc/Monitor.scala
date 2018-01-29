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

import com.thenetcircle.event_bus.context.AppContext
import com.thenetcircle.event_bus.interfaces.EventStatus.{Fail, InFB, Norm, ToFB}
import com.thenetcircle.event_bus.interfaces.{Event, EventStatus}
import kamon.Kamon
import kamon.metric.instrument.{Counter, InstrumentFactory}
import kamon.metric.{EntityRecorderFactory, GenericEntityRecorder}

import scala.collection.mutable

object Monitor {
  private var isKamonEnabled: Boolean = false

  def init()(implicit appContext: AppContext): Unit = {
    isKamonEnabled =
      appContext.getSystemConfig().getString("app.monitor.kamon.auto-start") == "yes"

    if (isKamonEnabled) {
      Kamon.start()
      appContext.addShutdownHook(Kamon.shutdown())
    }
  }

  def isEnabled(): Boolean = isKamonEnabled

}

class Monitor()(implicit appContext: AppContext) {}

trait MonitoringHelp {

  def getStoryMonitor(storyName: String): StoryMonitor = StoryMonitor(storyName)

}

class StoryMonitor(storyName: String) {

  import StoryMonitor._

  def newEvent(event: Event): StoryMonitor = {
    if (Monitor.isEnabled())
      Kamon.metrics.entity(StoryMetrics, storyName).newEvent.increment()
    this
  }

  def watchError(ex: Throwable): StoryMonitor = {
    if (Monitor.isEnabled())
      Kamon.metrics.entity(StoryMetrics, storyName).onException(ex, "Error").increment()
    this
  }

  def watchPayload(payload: (EventStatus, Event)): StoryMonitor = {
    if (Monitor.isEnabled()) {
      val (status, event) = payload
      val entity          = Kamon.metrics.entity(StoryMetrics, storyName)
      status match {
        case Norm =>
          entity.normEvent.increment()

        case ToFB(opEx) =>
          entity.toFallbackEvent.increment()
          if (opEx.isDefined) entity.onException(opEx.get, "ToFB").increment()

        case InFB =>
          entity.inFallbackEvent.increment()

        case Fail(ex) =>
          entity.failureEvent.increment()
          entity.onException(ex, "Fail").increment()
      }
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
    val newEvent: Counter                            = counter("new-event")
    val normEvent: Counter                           = counter("norm-event")
    val inFallbackEvent: Counter                     = counter("in-fallback-event")
    val toFallbackEvent: Counter                     = counter("to-fallback-event")
    val failureEvent: Counter                        = counter("failure-event")
    def onException(ex: Throwable, category: String) = counter(category + "." + ex.getClass.getName)
  }

  object StoryMetrics extends EntityRecorderFactory[StoryMetrics] {
    def category: String                                                   = "story"
    def createRecorder(instrumentFactory: InstrumentFactory): StoryMetrics = new StoryMetrics(instrumentFactory)
  }
}
