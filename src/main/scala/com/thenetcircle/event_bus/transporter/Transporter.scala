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
import com.thenetcircle.event_bus.pipeline.LeftPort
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPoint

class Transporter(settings: TransporterSettings,
                  entryPoints: Vector[EntryPoint],
                  pipelineLeftPortBuilder: () => LeftPort)(
    implicit system: ActorSystem,
    materializer: Materializer) {

  private val committer = Flow[Event]
    .filter(_.committer.isDefined)
    // TODO: take care of Supervision of mapAsync
    .mapAsync(settings.commitParallelism)(_.committer.get.commit())
    .to(Sink.ignore)

  // TODO: draw a graph in comments
  // TODO: error handler
  private lazy val stream: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL
      .create() { implicit builder =>
        import GraphDSL.Implicits._

        val transportParallelism = settings.transportParallelism

        val groupedEntryPoints = entryPoints.groupBy(_.priority.id)
        // IndexedSeq(6, 3, 1)
        var priorities = (for ((_priorityId, _) <- groupedEntryPoints)
          yield _priorityId).toIndexedSeq
        val prioritizedChannel =
          builder.add(MergePrioritized[Event](priorities))

        /** --------------- Work Flow ---------------- */
        // format: off

        for ((_priorityId, _entryPoints) <- groupedEntryPoints) {

          val targetChannel =
            prioritizedChannel.in(priorities.indexOf(_priorityId))

          if (_entryPoints.size > 1) {
            val merge = builder.add(Merge[Event](_entryPoints.size))
            for (i <- _entryPoints.indices) {
              _entryPoints(i).port ~> merge.in(i)
            }
            merge.out ~> targetChannel
          } else {
            _entryPoints(0).port ~> targetChannel
          }

        }

        if (transportParallelism > 1) {
          val balancer             = builder.add(Balance[Event](transportParallelism))
          val committerMerger      = builder.add(Merge[Event](transportParallelism))

          prioritizedChannel ~> balancer

          for (i <- 0 until transportParallelism) {

                                balancer.out(i) ~> pipelineLeftPortBuilder().port.async ~> committerMerger.in(i)

          }

                                                                                           committerMerger.out ~> committer.async
        }
        else {

          prioritizedChannel ~> pipelineLeftPortBuilder().port.async ~> committer.async

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

    implicit val materializer = ActorMaterializer(settings.materializerSettings, Some(settings.name))

    val entryPoints =
      settings.entryPointsSettings.map(s => {
        implicit val exector = EventExtractor(s.eventFormat)
        EntryPoint(s)
      })

    val pipelineLeftPortBuilder = () => {
      settings.pipelineFactory.getLeftPort(settings.pipelineName, settings.pipelineLeftPortConfig) match {
        case Some(lp) => lp
        case None =>
          throw new IllegalArgumentException(
            s"There is not LeftPort of ${settings.pipelineName} found " +
              s"according to the configuration ${settings.pipelineLeftPortConfig}")
      }
    }

    new Transporter(settings, entryPoints, pipelineLeftPortBuilder)
  }
}
