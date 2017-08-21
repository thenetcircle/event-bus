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
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{
  GraphDSL,
  MergePrioritized,
  Partition,
  RunnableGraph
}
import com.thenetcircle.event_bus.pipeline.Pipeline
import com.thenetcircle.event_bus.{Event, EventPriority}

class Transporter(settings: TransporterSettings,
                  entryPoints: Vector[TransporterEntryPoint],
                  pipeline: Pipeline)(implicit system: ActorSystem,
                                      materializer: Materializer) {

  // TODO draw a graph in comments
  lazy val stream: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL
      .create() { implicit builder =>
        import GraphDSL.Implicits._

        // IndexedSeq(6, 5, 4, 3, 2)
        val priorities = EventPriority.values.toIndexedSeq.reverse.map(_.id)

        entryPoints foreach {
          tep: TransporterEntryPoint =>
            val entryPointSettings = tep.settings

            val etpSource =
              tep.entryPoint.port
                .flatMapMerge(entryPointSettings.maxParallelSources, identity)
                .map(_.withPlusPriority(tep.settings.priority))

            val mergePrioritizedShape =
              builder.add(MergePrioritized[Event](priorities))

            val partitionShape =
              builder.add(
                Partition[Event](priorities.size,
                                 event =>
                                   priorities.indexOf(event.priority.id)))

            // format: off

            etpSource ~> partitionShape

            for (i <- priorities.indices) {

                         partitionShape.out(i) ~> mergePrioritizedShape.in(i)

            }

            // format: on

            // Here will create a new pipeline producer
            mergePrioritizedShape.out ~> pipeline.leftPort
        }

        ClosedShape
      }
      .named(settings.name)
  )

  // TODO add a transporter controller as a materialized value
  def run(): Unit = stream.run()

}

object Transporter {
  def apply(settings: TransporterSettings)(
      implicit system: ActorSystem): Transporter = {

    implicit val materializer = settings.materializerSettings match {
      case Some(_settings) => ActorMaterializer(_settings)
      case None            => ActorMaterializer()
    }

    val entryPoints =
      settings.transportEntryPointsSettings.map(TransporterEntryPoint(_))
    val pipeline = Pipeline(settings.pipelineName)

    new Transporter(settings, entryPoints, pipeline)
  }
}
