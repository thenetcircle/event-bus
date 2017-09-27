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

trait Pipeline {

  val pipelineType: PipelineType
  val pipelineSettings: PipelineSettings

  protected val inletId  = new AtomicInteger(0)
  protected val outletId = new AtomicInteger(0)

  def getNewInlet(pipelineInletSettings: PipelineInletSettings): PipelineInlet
  def getNewOutlet(pipelineOutletSettings: PipelineOutletSettings)(
      implicit materializer: Materializer): PipelineOutlet

}

trait PipelineInletSettings
trait PipelineOutletSettings

trait PipelineInlet {
  val pipeline: Pipeline
  val inletName: String
  val inletSettings: PipelineInletSettings

  val stream: Flow[Event, Event, NotUsed]
}

trait PipelineOutlet {
  val pipeline: Pipeline
  val outletName: String
  val outletSettings: PipelineOutletSettings

  val stream: Source[Source[Event, NotUsed], NotUsed]
  val committer: Sink[Event, NotUsed]
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
