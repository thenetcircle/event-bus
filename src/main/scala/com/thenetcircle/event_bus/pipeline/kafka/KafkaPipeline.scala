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

import akka.NotUsed
import akka.kafka.ConsumerMessage.{CommittableOffset, CommittableOffsetBatch}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import com.thenetcircle.event_bus._
import com.thenetcircle.event_bus.pipeline.PipelineType.PipelineType
import com.thenetcircle.event_bus.pipeline._
import com.thenetcircle.event_bus.tracing.{Tracing, TracingSteps}

class KafkaPipeline(override val pipelineSettings: KafkaPipelineSettings)
    extends Pipeline
    with Tracing {
  private val inletId = new AtomicInteger(0)
  private val outletId = new AtomicInteger(0)

  val pipelineName: String = pipelineSettings.name

  override val pipelineType: PipelineType = PipelineType.Kafka

  /** Returns a new [[PipelineInlet]] of the [[Pipeline]]
    *
    * Which will create a new producer with a new connection to Kafka internally
    * after the port got materialized
    *
    * @param settings settings object, needs [[KafkaPipelineInletSettings]]
    */
  override def getNewInlet(settings: PipelineInletSettings): KafkaPipelineInlet = {
    require(
      settings.isInstanceOf[KafkaPipelineInletSettings],
      "KafkaPipeline only accpect KafkaLPipelineInletSettings."
    )

    new KafkaPipelineInlet(
      this,
      s"$pipelineName-inlet-${inletId.getAndIncrement()}",
      settings.asInstanceOf[KafkaPipelineInletSettings]
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
    * @param settings settings object, needs [[KafkaPipelineOutletSettings]]
    */
  override def getNewOutlet(
      settings: PipelineOutletSettings
  )(implicit materializer: Materializer): KafkaPipelineOutlet = {

    require(
      settings.isInstanceOf[KafkaPipelineOutletSettings],
      "KafkaPipeline only accpect KafkaPipelineOutletSettings."
    )

    new KafkaPipelineOutlet(
      this,
      s"$pipelineName-outlet-${outletId.getAndIncrement()}",
      settings.asInstanceOf[KafkaPipelineOutletSettings]
    )

  }

  /** Acknowledges the event to [[Pipeline]]
    *
    * @param settings the committer parameters
    * @return the committing akka-stream stage
    */
  override def getCommitter(settings: PipelineCommitterSettings): Sink[Event, NotUsed] = {
    require(
      settings.isInstanceOf[KafkaPipelineCommitterSettings],
      "KafkaPipeline only accpect KafkaPipelineCommitterSettings."
    )

    val _settings = settings.asInstanceOf[KafkaPipelineCommitterSettings]

    Flow[Event]
      .collect {
        case e if e.hasContext("kafkaCommittableOffset") =>
          e.context("kafkaCommittableOffset")
            .asInstanceOf[CommittableOffset] -> e.tracingId
      }
      .batch(max = _settings.commitBatchMax, {
        case (committableOffset, tracingId) =>
          val batch = CommittableOffsetBatch.empty.updated(committableOffset)
          val tracingList = List[Long](tracingId)
          batch -> tracingList
      }) {
        case ((batch, tracingList), (committableOffset, tracingId)) =>
          batch.updated(committableOffset) -> tracingList.+:(tracingId)
      }
      .mapAsync(_settings.commitParallelism)(result => {
        result._2.foreach(tracer.record(_, TracingSteps.PIPELINE_COMMITTED))
        result._1.commitScaladsl()
      })
      .to(Sink.ignore)
  }
}

object KafkaPipeline {

  def apply(settings: KafkaPipelineSettings): KafkaPipeline =
    new KafkaPipeline(settings)

}
