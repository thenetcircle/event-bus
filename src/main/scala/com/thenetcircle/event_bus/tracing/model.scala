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

import scala.util.Random

trait Tracing {
  lazy val tracer: Tracer = Tracer()
}

class Tracer {
  import Tracer._

  private val submitter = getSubmitter

  def startTracing(): TracingMessage = {
    val tracingMessage = TracingMessage("Application")
    submitter.sample(tracingMessage, "EventBus")
    tracingMessage
  }

  def startService(parentTracingId: Long,
                   serviceName: String,
                   spanName: Option[String] = None): TracingMessage =
    startService(TracingMessage("Unknown", parentTracingId),
                 serviceName,
                 spanName)

  def startService(parentTracingMessage: TracingMessage,
                   serviceName: String,
                   spanName: Option[String] = None): TracingMessage = {
    val tracingMessage = TracingMessage(spanName.getOrElse("Started"),
                                        parentTracingMessage.tracingId)
    submitter.sample(tracingMessage, serviceName)
    submitter.createChild(tracingMessage, parentTracingMessage)
    tracingMessage
  }

  def finish(tracingMessage: TracingMessage): Unit =
    submitter.flush(tracingMessage)
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

}

case class TracingMessage(
    override val spanName: String,
    override val tracingId: Long
) extends TracingSupport

object TracingMessage {
  def apply(spanName: String): TracingMessage = {
    TracingMessage(spanName, newTracingId())
  }

  def newTracingId(): Long =
    System.currentTimeMillis() + Random.nextLong()
}
