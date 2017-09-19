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
import com.github.levkhomich.akka.tracing.{
  TracingExtension,
  TracingExtensionImpl,
  TracingSupport
}

trait Tracing {
  lazy val tracer: Tracer = Tracer()
}

class Tracer {
  import Tracer._

  private val submitter = getSubmitter

  def startApplication(): TracingMessage = {
    val tracingMessage = TracingMessage(createNewTracingId(this), "Application")
    submitter.sample(tracingMessage, "EventBus")
    tracingMessage
  }

  def start(serviceName: String,
            parentTracingId: Long,
            spanName: Option[String] = None): TracingMessage = {
    val parentTracingMessage = TracingMessage(parentTracingId, "unknown")
    start(serviceName, parentTracingMessage, spanName)
  }

  def start(serviceName: String,
            parentTracingMessage: TracingMessage,
            spanName: Option[String] = None): TracingMessage = {
    val tracingMessage = TracingMessage(parentTracingMessage.tracingId,
                                        spanName.getOrElse("Started"))
    submitter.sample(tracingMessage, serviceName)
    submitter.createChild(tracingMessage, parentTracingMessage)
    tracingMessage
  }

  def finish(tracingMessage: TracingMessage): Unit = ???
}

object Tracer {
  private var submitter: Option[TracingExtensionImpl] = None

  def init()(implicit system: ActorSystem): Unit =
    submitter = Some(TracingExtension(system))

  private def getSubmitter: TracingExtensionImpl = submitter match {
    case Some(t) => t
    case None    => throw new Exception("Tracer did not initialized yet.")
  }

  private val tracer  = new Tracer()
  def apply(): Tracer = tracer

  def createNewTracingId(obj: Any): Long = {
    val a = System.identityHashCode(obj)
    val b = hashCode
    a.toLong << 32 | b & 0xFFFFFFFFL
  }
}

case class TracingMessage(
    override val tracingId: Long,
    override val spanName: String
) extends TracingSupport
