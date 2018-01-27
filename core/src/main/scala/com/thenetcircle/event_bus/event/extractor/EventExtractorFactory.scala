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

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.util.ByteString
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat
import com.thenetcircle.event_bus.interfaces.Event

import scala.collection.mutable
import scala.concurrent.ExecutionContext

object EventExtractorFactory {

  private val registeredExtractors: mutable.Map[DataFormat, EventExtractor] = mutable.Map.empty

  def registerExtractor(extractor: EventExtractor): Unit =
    registeredExtractors += (extractor.getFormat() -> extractor)

  registerExtractor(new ActivityStreamsEventExtractor())

  val defaultExtractor: EventExtractor = getExtractor(DataFormat.ACTIVITYSTREAMS)

  /**
    * Returns [[EventExtractor]] based on [[DataFormat]]
    */
  def getExtractor(format: DataFormat): EventExtractor = registeredExtractors.getOrElse(format, defaultExtractor)

  /**
    * Returns Unmarshaller[ByteString, Event] based on [[DataFormat]]
    */
  def getByteStringUnmarshaller(
      format: DataFormat
  )(implicit executionContext: ExecutionContext): Unmarshaller[ByteString, Event] =
    Unmarshaller.apply(_ => data => getExtractor(format).extract(data.toArray))

  /**
    * Returns Unmarshaller[HttpEntity, Event] based on [[DataFormat]]
    */
  def getHttpEntityUnmarshaller(
      format: DataFormat
  )(implicit executionContext: ExecutionContext): Unmarshaller[HttpEntity, Event] =
    Unmarshaller.byteStringUnmarshaller andThen getByteStringUnmarshaller(format)

}
