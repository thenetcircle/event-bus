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

import com.typesafe.scalalogging.Logger
import org.slf4j.Marker

@SerialVersionUID(5118293220L)
final class TaskLogger private (val storyName: String, val underlying: Logger) extends Serializable {

  private[this] val messagePrefix = s"[$storyName]"

  def error(message: String): Unit = underlying.error(s"$messagePrefix $message")

  def error(message: String, cause: Throwable): Unit = underlying.error(s"$messagePrefix $message", cause)

  def error(message: String, args: Any*): Unit = underlying.error(s"$messagePrefix $message", args)

  def error(marker: Marker, message: String): Unit = underlying.error(marker, s"$messagePrefix $message")

  def error(marker: Marker, message: String, cause: Throwable): Unit =
    underlying.error(marker, s"$messagePrefix $message", cause)

  def error(marker: Marker, message: String, args: Any*): Unit =
    underlying.error(marker, s"$messagePrefix $message", args)

  def warn(message: String): Unit = underlying.warn(s"$messagePrefix $message")

  def warn(message: String, cause: Throwable): Unit = underlying.warn(s"$messagePrefix $message", cause)

  def warn(message: String, args: Any*): Unit = underlying.warn(s"$messagePrefix $message", args)

  def warn(marker: Marker, message: String): Unit = underlying.warn(marker, s"$messagePrefix $message")

  def warn(marker: Marker, message: String, cause: Throwable): Unit =
    underlying.warn(marker, s"$messagePrefix $message", cause)

  def warn(marker: Marker, message: String, args: Any*): Unit =
    underlying.warn(marker, s"$messagePrefix $message", args)

  def info(message: String): Unit = underlying.info(s"$messagePrefix $message")

  def info(message: String, cause: Throwable): Unit = underlying.info(s"$messagePrefix $message", cause)

  def info(message: String, args: Any*): Unit = underlying.info(s"$messagePrefix $message", args)

  def info(marker: Marker, message: String): Unit = underlying.info(marker, s"$messagePrefix $message")

  def info(marker: Marker, message: String, cause: Throwable): Unit =
    underlying.info(marker, s"$messagePrefix $message", cause)

  def info(marker: Marker, message: String, args: Any*): Unit =
    underlying.info(marker, s"$messagePrefix $message", args)

  def debug(message: String): Unit = underlying.debug(s"$messagePrefix $message")

  def debug(message: String, cause: Throwable): Unit = underlying.debug(s"$messagePrefix $message", cause)

  def debug(message: String, args: Any*): Unit = underlying.debug(s"$messagePrefix $message", args)

  def debug(marker: Marker, message: String): Unit = underlying.debug(marker, s"$messagePrefix $message")

  def debug(marker: Marker, message: String, cause: Throwable): Unit =
    underlying.debug(marker, s"$messagePrefix $message", cause)

  def debug(marker: Marker, message: String, args: Any*): Unit =
    underlying.debug(marker, s"$messagePrefix $message", args)

  def trace(message: String): Unit = underlying.trace(s"$messagePrefix $message")

  def trace(message: String, cause: Throwable): Unit = underlying.trace(s"$messagePrefix $message", cause)

  def trace(message: String, args: Any*): Unit = underlying.trace(s"$messagePrefix $message", args)

  def trace(marker: Marker, message: String): Unit = underlying.trace(marker, s"$messagePrefix $message")

  def trace(marker: Marker, message: String, cause: Throwable): Unit =
    underlying.trace(marker, s"$messagePrefix $message", cause)

  def trace(marker: Marker, message: String, args: Any*): Unit =
    underlying.trace(marker, s"$messagePrefix $message", args)

}

object TaskLogger {
  def apply(storyName: String, underlying: Logger): TaskLogger = new TaskLogger(storyName, underlying)
}
