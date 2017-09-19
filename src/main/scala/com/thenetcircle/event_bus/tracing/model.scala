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
  lazy val tracer: TracingExtensionImpl = Tracing.getTracer
}

object Tracing {
  private var _tracer: Option[TracingExtensionImpl] = None
  def initTracer()(implicit system: ActorSystem): Unit =
    _tracer = Some(TracingExtension(system))

  def getTracer: TracingExtensionImpl = _tracer match {
    case Some(t) => t
    case None    => throw new Exception("Tracing didnt initialized yet.")
  }
}

trait TracingMessage extends TracingSupport {
  override val tracingId: Long = {
    val a = System.identityHashCode(this)
    val b = hashCode
    a.toLong << 32 | b & 0xFFFFFFFFL
  }

  override val spanName: String = "Event"
}
