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
import akka.stream.scaladsl.{Flow, Source}
import com.thenetcircle.event_bus.{Event, EventFormat}

trait PipelineSettings {
  def name: String
}

trait Pipeline {

  val pipelineSettings: PipelineSettings

  protected val leftPortId  = new AtomicInteger(0)
  protected val rightPortId = new AtomicInteger(0)

  def leftPort(leftPortSettings: LeftPortSettings): LeftPort

  def rightPort(rightPortSettings: RightPortSettings)(
      implicit materializer: Materializer): RightPort

}

trait LeftPortSettings

trait LeftPort {
  def port: Flow[Event, Event, NotUsed]
}

trait RightPortSettings {
  val eventFormat: EventFormat
}

trait RightPort {
  def port: Source[Source[Event, NotUsed], NotUsed]
  def committer: Flow[Event, Event, NotUsed]
}
