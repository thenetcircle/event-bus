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

package com.thenetcircle.event_bus.tracing

import akka.actor.ActorSystem
import com.github.levkhomich.akka.tracing.{TracingExtension, TracingExtensionImpl, TracingSupport}
import com.thenetcircle.event_bus.event.Event

import scala.util.Random

class Tracer(te: TracingExtensionImpl) {
  def newTracing(): Long = {
    val tracingId = Tracer.newTracingId()
    te.sample(TracingMessage(tracingId, "Application"), "EventBus")
    tracingId
  }

  def resumeTracing(tracingId: Long): Long = {
    val message = TracingMessage(tracingId, "Application")
    te.exportMetadata(message)
      .foreach(md => te.importMetadata(message, md, "EventBus"))
    tracingId
  }

  def record(tracingId: Long, step: TracingStep): Unit = {
    val message = TracingMessage(tracingId, step.toString)
    te.record(message, step.toString)
    if (step.isInstanceOf[TracingEndingStep]) te.flush(message)
  }

  def record(tracingId: Long, info: String): Unit =
    te.record(TracingMessage(tracingId), info)

  def record(tracingId: Long, info: Throwable): Unit =
    te.record(TracingMessage(tracingId), info)

  def record(tracingId: Long, key: String, value: String): Unit =
    te.recordKeyValue(TracingMessage(tracingId), key, value)

  def record(tracingId: Long, event: Event): Unit = {
    record(tracingId, "UUID", event.metadata.uuid)
    record(tracingId, "name", event.metadata.name)
    event.metadata.provider.foreach(record(tracingId, "provider", _))
    event.metadata.actor
      .foreach(actor => record(tracingId, "actor", s"${actor.objectType}-${actor.id}"))
    record(tracingId, "timestamp", event.metadata.published.toString)
    record(tracingId, "channel", event.channel)
  }
}

object Tracer {
  private var tracer: Option[Tracer] = None

  def init(system: ActorSystem): Unit = {
    tracer = Some(new Tracer(TracingExtension(system)))
  }

  def apply(): Tracer = tracer match {
    case Some(t) => t
    case None    => throw new Exception("Tracer did not initialized yet.")
  }

  def newTracingId(): Long = System.currentTimeMillis() + Random.nextInt(10000)
}

case class TracingMessage(override val tracingId: Long, override val spanName: String)
    extends TracingSupport {}

object TracingMessage {
  def apply(tracingId: Long): TracingMessage =
    TracingMessage(tracingId, "Unknown")
}
