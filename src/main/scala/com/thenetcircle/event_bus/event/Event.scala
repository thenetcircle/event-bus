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

package com.thenetcircle.event_bus.event

import com.thenetcircle.event_bus.event.extractor._
import scala.concurrent.Future

case class Event(metadata: EventMetaData,
                 body: EventBody,
                 channel: String,
                 sourceType: EventSourceType,
                 tracingId: Long,
                 context: Map[String, Any] = Map.empty,
                 committer: Option[EventCommitter] = None) {

  def withCommitter(commitFunction: () => Future[Any]): Event =
    copy(committer = Some(new EventCommitter {
      override def commit(): Future[Any] = commitFunction()
    }))

  def hasContext(key: String): Boolean = context.isDefinedAt(key)

  def addContext(key: String, value: Any): Event =
    copy(context = context + (key -> value))

  def isFailed(): Boolean = false

  def uniqueName: String = ""

}
