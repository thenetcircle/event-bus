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
import com.thenetcircle.event_bus.pipeline.{
  PipelineInletSettings,
  PipelineSettings,
  PipelineOutletSettings
}

import scala.concurrent.duration.FiniteDuration
import KafkaPipeline._
import com.thenetcircle.event_bus.EventFormat
import com.thenetcircle.event_bus.EventFormat.DefaultFormat

case class KafkaPipelineSettings(
    name: String,
    producerSettings: ProducerSettings[Key, Value],
    consumerSettings: ConsumerSettings[Key, Value]
) extends PipelineSettings

case class KafkaPipelineInletSettings(
    produceParallelism: Option[Int] = None,
    dispatcher: Option[String] = None,
    properties: Option[Map[String, String]] = None,
    closeTimeout: Option[FiniteDuration] = None
) extends PipelineInletSettings

case class KafkaPipelineOutletSettings(
    groupId: String,
    extractParallelism: Int = 3,
    commitParallelism: Int = 3,
    commitBatchMax: Int = 20,
    eventFormat: EventFormat = DefaultFormat,
    topics: Option[Set[String]] = None,
    topicPattern: Option[String] = None,
    dispatcher: Option[String] = None,
    properties: Option[Map[String, String]] = None,
    pollInterval: Option[FiniteDuration] = None,
    pollTimeout: Option[FiniteDuration] = None,
    stopTimeout: Option[FiniteDuration] = None,
    closeTimeout: Option[FiniteDuration] = None,
    commitTimeout: Option[FiniteDuration] = None,
    wakeupTimeout: Option[FiniteDuration] = None,
    maxWakeups: Option[Int] = None
) extends PipelineOutletSettings
