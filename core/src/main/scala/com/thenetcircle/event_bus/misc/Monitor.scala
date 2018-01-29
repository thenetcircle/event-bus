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
      e.errors(ex).increment()
    })
    this
  }

  def onProcessed(status: EventStatus, event: Event): StoryMonitor = {
    status match {
      case Norm    => entity.foreach(_.normEvent.increment())
      case ToFB(_) => entity.foreach(_.toFBEvent.increment())
      case InFB    => entity.foreach(_.inFBEvent.increment())
      case Fail(_) => entity.foreach(_.failEvent.increment())
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
    val newEvent: Counter     = counter("new-event")
    val normEvent: Counter    = counter("processed.norm-event")
    val toFBEvent: Counter    = counter("processed.tofb-event")
    val inFBEvent: Counter    = counter("processed.infb-event")
    val failEvent: Counter    = counter("processed.fail-event")
    val completion: Counter   = counter("completion")
    val termination: Counter  = counter("termination")
    def errors(ex: Throwable) = counter("errors." + ex.getClass.getName.replaceAll("\\.", "_"))
  }

  object StoryMetrics extends EntityRecorderFactory[StoryMetrics] {
    def category: String                                                   = "story"
    def createRecorder(instrumentFactory: InstrumentFactory): StoryMetrics = new StoryMetrics(instrumentFactory)
  }
}
