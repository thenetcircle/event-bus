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
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.pipeline.PipelineType.PipelineType
import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader

trait PipelineSettings {
  val name: String
}
trait PipelineInletSettings
trait PipelineOutletSettings
trait PipelineCommitterSettings

trait Pipeline {
  val pipelineType: PipelineType
  val pipelineSettings: PipelineSettings

  def getNewInlet(settings: PipelineInletSettings): PipelineInlet

  def getNewOutlet(settings: PipelineOutletSettings)(
      implicit materializer: Materializer
  ): PipelineOutlet

  def getCommitter(settings: PipelineCommitterSettings): Sink[Event, NotUsed]
}

sealed trait PipelinePort {
  val pipeline: Pipeline
}

trait PipelineInlet extends PipelinePort {
  val inletName: String
  val inletSettings: PipelineInletSettings

  val stream: Flow[Event, Event, NotUsed]
}

trait PipelineOutlet extends PipelinePort {
  val outletName: String
  val outletSettings: PipelineOutletSettings

  val stream: Source[Source[Event, NotUsed], NotUsed]
}

object PipelineType extends Enumeration {
  type PipelineType = Value

  val Kafka = Value(1, "Kafka")

  def apply(name: String): PipelineType = name.toUpperCase match {
    case "KAFKA" => Kafka
  }

  implicit val pipelineTypeReader: ValueReader[PipelineType] =
    new ValueReader[PipelineType] {
      override def read(config: Config, path: String) =
        apply(config.getString(path))
    }
}
