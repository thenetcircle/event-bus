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

package com.thenetcircle.event_bus.event_extractor

import akka.util.ByteString
import com.thenetcircle.event_bus.EventFormat.TestFormat
import com.thenetcircle.event_bus.event_extractor.ExtractedPriority.ExtractedPriority
import com.thenetcircle.event_bus.{EventBody, EventFormat, EventMetaData}
import io.jvm.uuid.UUID

import scala.concurrent.{ExecutionContext, Future}

object ExtractedPriority extends Enumeration {
  type ExtractedPriority = Value
  val High   = Value(3, "High")
  val Normal = Value(2, "Normal")
  val Low    = Value(1, "Low")
}

case class ExtractedData(
    body: EventBody,
    metadata: EventMetaData,
    priority: ExtractedPriority = ExtractedPriority.Normal,
    channel: Option[String] = None
) {

  def withPriority(priority: ExtractedPriority): ExtractedData =
    copy(priority = priority)

  def withChannel(channel: String): ExtractedData =
    copy(channel = Some(channel))

}

trait EventExtractor {

  /**
    * Format of the extracted data
    * @return EventFormat
    */
  def format: EventFormat

  /**
    * Extract metadata from data accroding to Format
    *
    * @return ExtractedData
    */
  def extract(data: ByteString)(
      implicit executor: ExecutionContext): Future[ExtractedData]

}

object EventExtractor {

  /**
    * Generate a UUID
    *
    * @return String
    */
  def genUUID(): String = UUID.random.toString

  def apply(format: EventFormat): EventExtractor = format match {

    case EventFormat.DefaultFormat =>
      new TNCActivityStreamsExtractor with EventExtractor

    case EventFormat.TestFormat =>
      new TNCActivityStreamsExtractor with EventExtractor {
        override val format: EventFormat = TestFormat
      }

  }

}
