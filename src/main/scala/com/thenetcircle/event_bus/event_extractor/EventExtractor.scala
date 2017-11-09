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
import com.thenetcircle.event_bus.event_extractor.activity_streams.ActivityStreamsExtractor
import scala.concurrent.{ExecutionContext, Future}

trait EventExtractor {

  /** Format of the extracted data
    *
    * @return EventFormat
    */
  def format: EventFormat

  /** Extract metadata from data accroding to Format
    *
    * @return ExtractedData
    */
  def extract(data: ByteString)(implicit executor: ExecutionContext): Future[ExtractedData]

}

object EventExtractor {

  /** Default event extractor, based on ActivityStreams 1.0
    * http://activitystrea.ms/specs/json/1.0/
    */
  implicit val activityStreamsExtractor: EventExtractor =
    new ActivityStreamsExtractor

  /** Returns [[EventExtractor]] based on [[EventFormat]]
    *
    * @param format
    */
  def apply(format: EventFormat): EventExtractor = format match {
    // case EventFormat.DefaultFormat => activityStreamsExtractor
    case _ => activityStreamsExtractor
  }
}
