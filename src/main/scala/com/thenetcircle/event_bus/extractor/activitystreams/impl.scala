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

package com.thenetcircle.event_bus.extractor.activitystreams

import spray.json._

case class Activity(title: String,
                    id: Option[String],
                    published: Option[String],
                    verb: Option[String],
                    actor: Option[ActivityObject],
                    // `object`: Option[ActivityObject],
                    // target: Option[ActivityObject],
                    provider: Option[ActivityObject],
                    // content: Option[Any],
                    // generator: Option[ActivityObject]
)

case class ActivityObject(id: Option[String],
                          objectType: Option[String],
                          // attachments: Option[List[ActivityObject]],
                          // content: Option[Any],
                          // summary: Option[Any],
                          // downstreamDuplicates: Option[List[String]],
                          // upstreamDuplicates: Option[List[String]],
                          // author: Option[ActivityObject]
)

object ActivityStreamsProtocol extends DefaultJsonProtocol {
  implicit val activityObjectFormat = jsonFormat2(ActivityObject)
  implicit val activityFormat       = jsonFormat6(Activity)
}
