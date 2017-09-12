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

import akka.kafka.{ConsumerSettings, ProducerSettings}
import com.thenetcircle.event_bus.EventFormat
import com.thenetcircle.event_bus.pipeline.kafka.KafkaPipeline.{Key, Value}
import com.thenetcircle.event_bus.pipeline.{
  PipelineInletSettings,
  PipelineOutletSettings,
  PipelineSettings
}

import scala.concurrent.duration.FiniteDuration

case class KafkaPipelineSettings(
    name: String,
    producerSettings: ProducerSettings[Key, Value],
    consumerSettings: ConsumerSettings[Key, Value]
) extends PipelineSettings

case class KafkaPipelineInletSettings(
    closeTimeout: Option[FiniteDuration],
    parallelism: Option[Int]
) extends PipelineInletSettings

case class KafkaPipelineOutletSettings(
    groupId: String,
    extractParallelism: Int,
    commitParallelism: Int,
    commitBatchMax: Int,
    eventFormat: EventFormat,
    topics: Option[Set[String]],
    topicPattern: Option[String],
    pollInterval: Option[FiniteDuration],
    pollTimeout: Option[FiniteDuration],
    stopTimeout: Option[FiniteDuration],
    closeTimeout: Option[FiniteDuration],
    commitTimeout: Option[FiniteDuration],
    wakeupTimeout: Option[FiniteDuration],
    maxWakeups: Option[Int]
) extends PipelineOutletSettings
