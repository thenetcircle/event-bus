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

package com.thenetcircle.event_bus.extractor

import akka.util.ByteString
import com.thenetcircle.event_bus.{ EventFormat, EventMetaData, EventPriority }
import io.jvm.uuid.UUID

trait Extractor[Fmt <: EventFormat] {

  /**
   * Extract metadata from data accroding to Format
   *
   * @return (EventMetaData, Option[Channel], Option[EventPriority])
   */
  def extract(data: ByteString): (EventMetaData, Option[String], Option[EventPriority])

}

object Extractor {

  implicit val defaultFormatExtractor: Extractor[EventFormat.DefaultFormat] =
    new TNCActivityStreamsExtractor with Extractor[EventFormat.DefaultFormat]

  /**
   * Generate a UUID
   *
   * @return String
   */
  def genUUID(): String = UUID.random.toString

}
