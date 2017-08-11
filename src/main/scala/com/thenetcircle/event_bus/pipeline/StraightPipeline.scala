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

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ BroadcastHub, Keep, MergeHub, Sink, Source }

case class StraightPipelineSettings(
    name: String = "DefaultStraightPipeline"
) extends PipelineSettings {
  def withName(name: String): StraightPipelineSettings = copy(name = name)
}

class StraightPipeline(pipelineSettings: StraightPipelineSettings)(implicit materializer: Materializer)
    extends Pipeline(pipelineSettings) {

  private val (sink, source) =
    MergeHub
      .source[In](perProducerBufferSize = 16)
      .toMat(BroadcastHub.sink[Out](bufferSize = 256))(Keep.both)
      .run()

  /**
   * Get a inlet of the pipeline
   */
  def inlet(): Sink[In, NotUsed] = sink.named(s"$pipelineName-inlet-${inletId.getAndIncrement()}")

  /**
   * Get a outlet of the pipeline
   */
  def outlet(): Source[Out, NotUsed] = source.named(s"$pipelineName-outlet-${outletId.getAndIncrement()}")

}

object StraightPipeline {

  def apply(pipelineSettings: StraightPipelineSettings)(implicit materializer: Materializer): StraightPipeline =
    new StraightPipeline(pipelineSettings)

}
