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

package com.thenetcircle.event_bus.event.extractor

import akka.util.ByteString
import com.thenetcircle.event_bus.event.{EventFormat, ExtractedEvent}
import com.thenetcircle.event_bus.event.extractor.activitystreams.ActivityStreamsExtractor

import scala.concurrent.{ExecutionContext, Future}

trait IExtractor {

  /** Format of the extracted data
    *
    * @return EventFormat
    */
  def format: EventFormat

  /** Extract metadata from data accroding to Format
    *
    * @return ExtractedEvent
    */
  def extract(data: ByteString)(implicit executor: ExecutionContext): Future[ExtractedEvent]

}

object IExtractor {

  /** Default event extractor, based on ActivityStreams 1.0
    * http://activitystrea.ms/specs/json/1.0/
    */
  implicit val activityStreamsExtractor: IExtractor =
    new ActivityStreamsExtractor

  /** Returns [[IExtractor]] based on [[EventFormat]]
    *
    * @param format
    */
  def apply(format: EventFormat): IExtractor = format match {
    // case EventFormat.DefaultFormat => activityStreamsExtractor
    case _ => activityStreamsExtractor
  }
}
