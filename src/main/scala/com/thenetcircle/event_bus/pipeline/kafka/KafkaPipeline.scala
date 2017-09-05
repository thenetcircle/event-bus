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

package com.thenetcircle.event_bus.pipeline.kafka

import akka.stream.Materializer
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.event_extractor.EventExtractor
import com.thenetcircle.event_bus.pipeline._

class KafkaPipeline(pipelineSettings: KafkaPipelineSettings) extends Pipeline {

  val pipelineName: String = pipelineSettings.name

  type LPS = KafkaLeftPortSettings
  type RPS = KafkaRightPortSettings

  /** Returns a new [[LeftPort]] of the [[Pipeline]]
    *
    * Which will create a new producer with a new connection to Kafka internally after the port got materialized
    *
    * @param leftPortSettings settings object, needs [[KafkaLeftPortSettings]]
    */
  override def leftPort(leftPortSettings: LPS): KafkaLeftPort =
    new KafkaLeftPort(s"$pipelineName-leftport-${leftPortId.getAndIncrement()}",
                      pipelineSettings,
                      leftPortSettings)

  /** Returns a new [[RightPort]] of the [[Pipeline]]
    *
    * Which will create a new consumer to the kafka Cluster after the port got materialized, It expressed as a Source[Source[Event, _], _]
    * Each (topic, partition) will be presented as a Source[Event, NotUsed]
    * After each [[Event]] got processed, It needs to be commit, There are two ways to do that:
    * 1. Call the committer of the [[Event]] for committing the single [[Event]]
    * 2. Use the committer of the [[RightPort]] (Batched, Recommended)
    *
    * @param rightPortSettings settings object, needs [[KafkaRightPortSettings]]
    */
  override def rightPort(rightPortSettings: RPS)(
      implicit materializer: Materializer): KafkaRightPort = {

    implicit val eventExtractor: EventExtractor = EventExtractor(
      rightPortSettings.eventFormat)

    new KafkaRightPort(
      s"$pipelineName-rightport-${rightPortId.getAndIncrement()}",
      pipelineSettings,
      rightPortSettings)
  }

}

object KafkaPipeline {

  type Key   = Array[Byte]
  type Value = Array[Byte]

  def apply(settings: KafkaPipelineSettings): KafkaPipeline =
    new KafkaPipeline(settings)

}
