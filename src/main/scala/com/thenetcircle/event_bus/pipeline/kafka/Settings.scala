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
  BatchCommitSettings,
  LeftPortSettings,
  PipelineSettings,
  RightPortSettings
}

import scala.concurrent.duration.FiniteDuration

case class KafkaPipelineSettings(
    name: String,
    producerSettings: ProducerSettings[KafkaPipeline.Key, KafkaPipeline.Value],
    consumerSettings: ConsumerSettings[KafkaPipeline.Key, KafkaPipeline.Value]
) extends PipelineSettings

case class KafkaLeftPortSettings(
    commitParallelism: Int = 10,
    produceParallelism: Option[Int] = None,
    dispatcher: Option[String] = None,
    properties: Option[Map[String, String]] = None,
    closeTimeout: Option[FiniteDuration] = None
) extends LeftPortSettings

case class KafkaRightPortSettings(
    groupId: String,
    extractParallelism: Int = 3,
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
) extends RightPortSettings

case class KafkaBatchCommitSettings(
    parallelism: Int = 3,
    batchMax: Int = 20
) extends BatchCommitSettings
