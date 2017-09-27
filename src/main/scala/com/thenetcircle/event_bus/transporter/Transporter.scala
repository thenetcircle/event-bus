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
import com.thenetcircle.event_bus.pipeline.PipelineInlet
import com.thenetcircle.event_bus.transporter.entrypoint.EntryPoint
import com.typesafe.scalalogging.StrictLogging
import ActorAttributes.supervisionStrategy
import Supervision.resumingDecider
import akka.stream.scaladsl.RestartFlow

import scala.concurrent.duration._

class Transporter(settings: TransporterSettings,
                  entryPoints: Vector[EntryPoint],
                  pipelineInletGetter: () => PipelineInlet)(
    implicit system: ActorSystem,
    materializer: Materializer)
    extends StrictLogging {

  logger.info(s"new Transporter ${settings.name} is created")

  private val committer = Flow[Event]
    .filter(_.committer.isDefined)
    .mapAsync(settings.commitParallelism)(event =>
      event.committer.get
        .commit()
        .map(_ => {
          logger.debug(
            s"Event(${event.metadata.uuid}, ${event.metadata.name}) is committed.")
        })(materializer.executionContext))
    .withAttributes(supervisionStrategy(resumingDecider))
    .to(Sink.ignore)

  private def pipelineInlet(): Flow[Event, Event, NotUsed] =
    RestartFlow.withBackoff[Event, Event](
      minBackoff = 1.second,
      maxBackoff = 10.minutes,
      randomFactor = 0.1
    ) { () =>
      logger.info(
        s"Creating a inlet of pipeline ${settings.pipeline.pipelineSettings.name}")
      pipelineInletGetter().stream
    }

  // TODO: draw a graph in comments
  // TODO: error handler
  private lazy val stream: RunnableGraph[NotUsed] = RunnableGraph.fromGraph(
    GraphDSL
      .create() { implicit builder =>
        import GraphDSL.Implicits._

        val transportParallelism = settings.transportParallelism

        val groupedEntryPoints = entryPoints.groupBy(_.settings.priority.id)
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
              _entryPoints(i).stream ~> merge.in(i)
            }
            merge.out ~> targetChannel
          } else {
            _entryPoints(0).stream ~> targetChannel
          }

        }

        if (transportParallelism > 1) {
          val balancer             = builder.add(Balance[Event](transportParallelism))
          val committerMerger      = builder.add(Merge[Event](transportParallelism))

          prioritizedChannel ~> balancer

          for (i <- 0 until transportParallelism) {

                                balancer.out(i) ~> pipelineInlet() ~> committerMerger.in(i)

          }

                                                                      committerMerger.out ~> committer.async
        }
        else {

          prioritizedChannel ~> pipelineInlet() ~> committer.async

        }

        ClosedShape
      }
      .named(settings.name)
  )

  // TODO add a transporter controller as a materialized value
  def run(): Unit = {
    logger.info(s"running Transporter ${settings.name}")
    stream.run()
  }
}

object Transporter extends StrictLogging {
  def apply(settings: TransporterSettings)(
      implicit system: ActorSystem): Transporter = {

    logger.info(s"Creating a new Transporter ${settings.name} from TransporterSettings")

    implicit val materializer = ActorMaterializer(settings.materializerSettings, Some(settings.name))

    val entryPoints =
      settings.entryPointsSettings.map(s => {
        implicit val exector = EventExtractor(s.eventFormat)
        EntryPoint(s)
      })

    val pipelineInletGetter = () => settings.pipeline.getNewInlet(settings.pipelineInletSettings)

    new Transporter(settings, entryPoints, pipelineInletGetter)
  }
}
