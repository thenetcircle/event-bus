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
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.extractor.DataFormat.DataFormat

import scala.concurrent.{ExecutionContext, Future}

trait EventExtractor {

  /**
   * Get the data format of this extractor
   *
   * @return [[DataFormat]]
   */
  def getFormat(): DataFormat

  /**
   * Extract event from data according to data format
   *
   * @return [[Future[Event]]
   */
  def extract(data: ByteString, passThrough: Option[Any] = None)(
      implicit executionContext: ExecutionContext
  ): Future[Event]

}
