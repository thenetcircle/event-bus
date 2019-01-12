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

package com.thenetcircle.event_bus.story

import com.thenetcircle.event_bus.story.interfaces.ITask
import com.typesafe.scalalogging.{Logger => Underlying}
import org.slf4j.MDC

object StoryLogger {

  var instances: Map[String, StoryLogger] = Map.empty

  def apply(storyName: String): StoryLogger =
    instances.getOrElse(storyName, {
      val logger = new StoryLogger(storyName)(Underlying(classOf[ITask]))
      instances += (storyName -> logger)
      logger
    })

}

@SerialVersionUID(517223220L)
final class StoryLogger private (val storyName: String)(val underlying: Underlying) extends Serializable {

  private def withMDC(logging: => Unit): Unit = {
    MDC.put("storyName", storyName)
    logging
    // MDC.remove("storyName")
  }

  def error(message: String): Unit = withMDC(underlying.error(message))

  def error(message: String, cause: Throwable): Unit = withMDC(underlying.error(message, cause))

  def error(message: String, args: Any*): Unit = withMDC(underlying.error(message, args))

  def warn(message: String): Unit = withMDC(underlying.warn(message))

  def warn(message: String, cause: Throwable): Unit = withMDC(underlying.warn(message, cause))

  def warn(message: String, args: Any*): Unit = withMDC(underlying.warn(message, args))

  def info(message: String): Unit = withMDC(underlying.info(message))

  def info(message: String, cause: Throwable): Unit = withMDC(underlying.info(message, cause))

  def info(message: String, args: Any*): Unit = withMDC(underlying.info(message, args))

  def debug(message: String): Unit = withMDC(underlying.debug(message))

  def debug(message: String, cause: Throwable): Unit = withMDC(underlying.debug(message, cause))

  def debug(message: String, args: Any*): Unit = withMDC(underlying.debug(message, args))

  def trace(message: String): Unit = withMDC(underlying.trace(message))

  def trace(message: String, cause: Throwable): Unit = withMDC(underlying.trace(message, cause))

  def trace(message: String, args: Any*): Unit = withMDC(underlying.trace(message, args))

}
