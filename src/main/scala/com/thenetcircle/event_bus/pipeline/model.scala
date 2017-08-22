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
import akka.stream.scaladsl.{Sink, Source}
import com.thenetcircle.event_bus.Event

trait PipelineSettings {
  def name: String
}

trait LeftPortSettings
trait RightPortSettings
trait BatchCommitSettings

trait LeftPortSettingsBuilder {
  def getSettings[T <: LeftPortSettings]: T
}

trait RightPortSettingsBuilder {
  def getSettings[T <: RightPortSettings]: T
}

trait BatchCommitSettingsBuilder {
  def getSettings[T <: BatchCommitSettings]: T
}

trait Pipeline {

  import Pipeline._

  protected val leftPortId  = new AtomicInteger(0)
  protected val rightPortId = new AtomicInteger(0)

  def leftPort: LeftPort
  def rightPort: RightPort

}

object Pipeline {

  trait LeftPort {
    def port: Sink[Event, NotUsed]
  }

  trait RightPort {
    def port: Source[Source[Event, NotUsed], _]
    def commit: Sink[Event, NotUsed]
  }

  private val pipelines = Map.empty[String, Pipeline]

  def apply(pipelineSettings: PipelineSettings): Pipeline = ???

  def apply(name: String): Pipeline = ???

}
