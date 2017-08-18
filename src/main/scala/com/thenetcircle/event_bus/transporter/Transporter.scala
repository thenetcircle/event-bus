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

package com.thenetcircle.event_bus.transporter

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{GraphDSL, MergePreferred, Partition, RunnableGraph}
import com.thenetcircle.event_bus.{Event, EventPriority, EventSourceType}

class Transporter(settings: TransporterSettings)(
    implicit materializer: Materializer) {

  private val entryPoints = settings.entryPoints
  private val pipeline    = settings.pipeline

  lazy val stream: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL
      .create() { implicit builder =>
        import GraphDSL.Implicits._

        val merge = builder.add(MergePreferred[Event](entryPoints.size))

        // high priority and fallback events to partition 0, others go to 1
        val partition = builder.add(
          Partition[Event](
            2,
            e =>
              if (e.priority == EventPriority.High || e.sourceType == EventSourceType.Fallback)
                0
              else 1)
        )

        var i = 0
        entryPoints foreach { ep =>
          // ep.port ~> partition
          partition.out(0) ~> merge.preferred
          partition.out(1) ~> merge.in(i)
          i = i + 1
        }

        merge.out ~> pipeline.leftPort

        ClosedShape
      }
      .named(settings.name)
  )

  def run(): Unit = stream.run()

}

object Transporter {
  def apply(settings: TransporterSettings)(
      implicit materializer: Materializer): Transporter =
    new Transporter(settings)
}
