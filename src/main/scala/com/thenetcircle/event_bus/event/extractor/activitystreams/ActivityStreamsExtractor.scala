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

package com.thenetcircle.event_bus.event.extractor.activitystreams

import java.text.SimpleDateFormat

import io.jvm.uuid.UUID
import akka.util.ByteString
import com.thenetcircle.event_bus.event._
import com.thenetcircle.event_bus.event.extractor.EventFormat.EventFormat
import com.thenetcircle.event_bus.event.extractor._
import com.typesafe.scalalogging.StrictLogging
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

class ActivityStreamsExtractor extends IExtractor with StrictLogging {

  import ActivityStreamsProtocol._

  override val format: EventFormat = EventFormat.ACTIVITYSTREAMS

  override def extract(
      data: ByteString
  )(implicit executor: ExecutionContext): Future[ExtractedData] = Future {
    try {
      val jsonAst  = data.utf8String.parseJson
      val activity = jsonAst.convertTo[Activity]

      val uuid: String = activity.id.getOrElse(UUID.random.toString)
      val name: String = activity.title
      val published: Long = activity.published match {
        case Some(p) =>
          new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
            .parse(p)
            .getTime

        case None =>
          System.currentTimeMillis()
      }
      val provider = activity.provider.flatMap(_.id)
      val actor    = activity.actor.flatMap(actor => Some(actor.id.getOrElse("")))

      ExtractedData(metadata = EventMetaData(uuid, name, published, provider, actor),
                    body = EventBody(data, format))
    } catch {
      case ex: Throwable =>
        logger.warn(s"Parsing data ${data.utf8String} failed with error: ${ex.getMessage}")
        throw ex
    }
  }

}
