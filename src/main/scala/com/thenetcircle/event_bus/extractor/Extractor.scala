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
import com.thenetcircle.event_bus.EventFormat.DefaultFormat
import com.thenetcircle.event_bus.{ EventBody, EventFormat, EventMetaData, EventPriority }
import io.jvm.uuid.UUID

import scala.concurrent.{ ExecutionContext, Future }

case class ExtractedData(
    body: EventBody[EventFormat],
    metadata: EventMetaData,
    channel: Option[String] = None,
    priority: Option[EventPriority] = None
) {
  def withChannel(channel: String): ExtractedData = copy(channel = Some(channel))
  def withPriority(priority: EventPriority): ExtractedData = copy(priority = Some(priority))
}

trait Extractor[+Fmt <: EventFormat] {

  /**
   * Extract metadata from data accroding to Format
   *
   * @return ExtractedData
   */
  def extract(data: ByteString)(implicit executor: ExecutionContext): Future[ExtractedData]

  def dataFormat: Fmt

}

object Extractor {

  implicit val defaultFormatExtractor: Extractor[DefaultFormat] =
    new TNCActivityStreamsExtractor with Extractor[DefaultFormat] {
      override val dataFormat: DefaultFormat = DefaultFormat

      override def getEventBody(data: ByteString): EventBody[DefaultFormat] =
        EventBody(data, DefaultFormat)
    }

  /**
   * Generate a UUID
   *
   * @return String
   */
  def genUUID(): String = UUID.random.toString

}
