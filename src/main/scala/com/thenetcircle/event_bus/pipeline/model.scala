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
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.event_extractor.EventExtractor

trait PipelineSettings {
  def name: String
}
trait LeftPortSettings
trait RightPortSettings
trait BatchCommitSettings

//https://stackoverflow.com/questions/4626904/scala-generic-method-overriding
trait Pipeline[A, B, C] {

  protected val leftPortId  = new AtomicInteger(0)
  protected val rightPortId = new AtomicInteger(0)

  def leftPort(leftPortSettings: A)(
      implicit system: ActorSystem,
      materializer: Materializer): Sink[Event, NotUsed]

  def rightPort(rightPortsettings: B)(
      implicit system: ActorSystem,
      materializer: Materializer,
      extractor: EventExtractor): Source[Source[Event, NotUsed], _]

  def batchCommit(batchCommitSettings: C): Sink[Event, NotUsed]

}

object Pipeline {

  private val pipelines = Map.empty[String, Pipeline[_, _, _]]

  def apply(pipelineSettings: PipelineSettings): Pipeline[_, _, _] = ???

  def apply(name: String): Pipeline[_, _, _] = ???

}
