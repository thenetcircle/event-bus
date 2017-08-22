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

package com.thenetcircle.event_bus.pipeline

import java.util.concurrent.atomic.AtomicInteger

import akka.stream.scaladsl.Sink
import com.thenetcircle.event_bus.Event

sealed trait PipelineSettings {
  def name: String
}

case class KafkaPipelineSettings(
    name: String,
    bootstrapServers: String,
    producerClientSettings: Map[String, String] = Map.empty,
    consumerClientSettings: Map[String, String] = Map.empty,
    dispatcher: Option[String] = None
) extends PipelineSettings

sealed trait RightPortSettings

case class KafkaRightPortSettings(
    groupId: String,
    topics: Option[Set[String]] = None,
    topicPattern: Option[String] = None,
    extractorParallelism: Int = 3
) extends RightPortSettings

abstract class Pipeline(pipelineSettings: PipelineSettings) {

  protected val pipelineName: String = pipelineSettings.name
  protected val leftPortId           = new AtomicInteger(0)
  protected val rightPortId          = new AtomicInteger(0)

  def leftPort: Sink[Event, _]

  /*def rightPort(portSettings: RightPortSettings)(
      implicit extractor: Extractor[EventFormat]
  ): Source[Source[Event, _], _]*/

}

object Pipeline {

  private val pipelines = Map.empty[String, Pipeline]

  def apply(pipelineSettings: PipelineSettings): Pipeline = ???

  def apply(name: String): Pipeline = ???

}
