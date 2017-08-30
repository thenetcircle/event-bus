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
  Balance,
  Flow,
  GraphDSL,
  Merge,
  MergePrioritized,
  RunnableGraph,
  Sink
}
import com.thenetcircle.event_bus.Event
import com.thenetcircle.event_bus.event_extractor.EventExtractor
import com.thenetcircle.event_bus.pipeline.Pipeline.LeftPort
import com.thenetcircle.event_bus.pipeline.PipelineFactory
import com.thenetcircle.event_bus.transporter.entrypoint.{
  EntryPoint,
  EntryPointPriority
}

class Transporter(settings: TransporterSettings,
                  entryPoints: Vector[EntryPoint],
                  pipelineLeftPortBuilder: () => LeftPort,
                  committer: Sink[Event, NotUsed])(implicit system: ActorSystem,
                                                   materializer: Materializer) {

  // TODO: draw a graph in comments
  // TODO: error handler
  // TODO: parallel and async
  // TODO: one entrypoint onely to one leftport of pipeline?
  private lazy val stream: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL
      .create() { implicit builder =>
        import GraphDSL.Implicits._

        val transportParallelism = settings.transportParallelism
        val balancer             = builder.add(Balance[Event](transportParallelism))
        val committerMerger      = builder.add(Merge[Event](transportParallelism))

        // IndexedSeq(6, 3, 1)
        val priorities =
          EntryPointPriority.values.toIndexedSeq.reverse.map(_.id)
        val prioritizedChannel =
          builder.add(MergePrioritized[Event](priorities))

        /** --------------- Work Flow ---------------- */
        // format: off

        for ((priorityId, priorityGroupedEntryPoints) <- entryPoints.groupBy(_.priority.id)) {

          val targetChannel =
            prioritizedChannel.in(priorities.indexOf(priorityId))

          if (priorityGroupedEntryPoints.size > 1) {
            val merge = builder.add(Merge[Event](priorityGroupedEntryPoints.size))
            for (i <- priorityGroupedEntryPoints.indices) {
              priorityGroupedEntryPoints(i).port ~> merge.in(i)
            }
            merge.out ~> targetChannel
          } else {
            priorityGroupedEntryPoints(0).port ~> targetChannel
          }

        }

        prioritizedChannel ~> balancer

        for (i <- 0 until transportParallelism) {

                              balancer.out(i) ~> pipelineLeftPortBuilder().port.async ~> committerMerger.in(i)

        }

                                                                                         committerMerger.out ~> committer.async

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
      settings.entryPointsSettings.map(s => {
        implicit val exector = EventExtractor(s.eventFormat)
        EntryPoint(s)
      })

    val pipelineLeftPortBuilder = () => {
      PipelineFactory.getLeftPort(settings.pipelineName,
                                  settings.pipelineLeftPortConfig)
    }

    // TODO: move into class
    val committer = Flow[Event]
      .filter(_.committer.isDefined)
      // TODO: take care of Supervision of mapAsync
      .mapAsync(settings.commitParallelism)(_.committer.get.commit())
      .to(Sink.ignore)

    new Transporter(settings, entryPoints, pipelineLeftPortBuilder, committer)
  }
}
