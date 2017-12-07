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

import java.util.concurrent.atomic.AtomicInteger

import akka.kafka.{ConsumerSettings, ProducerSettings}
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.pipeline.model.PipelineType.PipelineType
import com.thenetcircle.event_bus.pipeline.model._

class KafkaPipeline(override val settings: KafkaPipelineSettings) extends Pipeline {

  override val _type: PipelineType = PipelineType.Kafka

  val pipelineName: String = settings.name
  private val inletId = new AtomicInteger(0)
  private val outletId = new AtomicInteger(0)

  /** Returns a new [[PipelineInlet]] of the [[Pipeline]]
    *
    * Which will create a new producer with a new connection to Kafka internally
    * after the port got materialized
    *
    * @param inletSettings settings object, needs [[KafkaPipelineInletSettings]]
    */
  override def createInlet(inletSettings: PipelineInletSettings): KafkaPipelineInlet = {
    require(
      inletSettings.isInstanceOf[KafkaPipelineInletSettings],
      "KafkaPipeline only accpect KafkaLPipelineInletSettings."
    )

    new KafkaPipelineInlet(
      this,
      s"$pipelineName-inlet-${inletId.getAndIncrement()}",
      inletSettings.asInstanceOf[KafkaPipelineInletSettings]
    )
  }

  /** Returns a new [[PipelineOutlet]] of the [[Pipeline]]
    *
    * Which will create a new consumer to the kafka Cluster after the port got materialized,
    * It expressed as a Source[Source[Event, _], _]
    * Each (topic, partition) will be presented as a Source[Event, NotUsed]
    * After each [[Event]] got processed, It needs to be commit, There are two ways to do that:
    * 1. Call the committer of the [[Event]] for committing the single [[Event]]
    * 2. Use the committer of the [[PipelineOutlet]] (Batched, Recommended)
    *
    * @param outletSettings settings object, needs [[KafkaPipelineOutletSettings]]
    */
  override def createOutlet(outletSettings: PipelineOutletSettings): KafkaPipelineOutlet = {
    require(
      outletSettings.isInstanceOf[KafkaPipelineOutletSettings],
      "KafkaPipeline only accpect KafkaPipelineOutletSettings."
    )

    new KafkaPipelineOutlet(
      this,
      s"$pipelineName-outlet-${outletId.getAndIncrement()}",
      outletSettings.asInstanceOf[KafkaPipelineOutletSettings]
    )
  }

}

object KafkaPipeline {

  def apply(settings: KafkaPipelineSettings): KafkaPipeline =
    new KafkaPipeline(settings)

}

case class KafkaPipelineSettings(name: String,
                                 producerSettings: ProducerSettings[ProducerKey, ProducerValue],
                                 consumerSettings: ConsumerSettings[ConsumerKey, ConsumerValue])
    extends PipelineSettings
