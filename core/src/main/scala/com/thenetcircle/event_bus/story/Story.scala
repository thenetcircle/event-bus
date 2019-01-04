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

package com.thenetcircle.event_bus.story

import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition}
import akka.{Done, NotUsed}
import com.thenetcircle.event_bus.context.TaskRunningContext
import com.thenetcircle.event_bus.event.Event
import com.thenetcircle.event_bus.event.EventStatus.{NORM, TOFB}
import com.thenetcircle.event_bus.misc.{Logging, Monitoring}
import com.thenetcircle.event_bus.story.StoryStatus.StoryStatus
import com.thenetcircle.event_bus.story.interfaces._

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

case class StorySettings(name: String, status: StoryStatus = StoryStatus.INIT)

class Story(
    val settings: StorySettings,
    val sourceTask: ISourceTask,
    val sinkTask: ISinkTask,
    val transformTasks: Option[List[ITransformTask]] = None,
    val fallbackTask: Option[IFallbackTask] = None
) extends Logging
    with Monitoring {

  // initialize internal status
  val storyName: String                           = settings.name
  private var storyStatus: StoryStatus            = settings.status
  private var runningFuture: Option[Future[Done]] = None

  private def getTaskClassName(t: ITask): String = Option(t.getClass.getSimpleName).getOrElse("")

  // initialize tasks
  sourceTask.initTask(s"story:$storyName#source:${getTaskClassName(sourceTask)}", this)
  sinkTask.initTask(s"story:$storyName#sink:${getTaskClassName(sinkTask)}", this)
  transformTasks.foreach(_.zipWithIndex.foreach {
    case (tt, i) => tt.initTask(s"story:$storyName#transform:$i:${getTaskClassName(tt)}", this)
  })
  fallbackTask.foreach(ft => ft.initTask(s"story:$storyName#fallback:${getTaskClassName(ft)}", this))

  def updateStoryStatus(status: StoryStatus): Unit = storyStatus = status
  def getStoryStatus(): StoryStatus                = storyStatus

  def combineStoryFlow()(implicit runningContext: TaskRunningContext): Flow[Payload, Payload, StoryMat] = {
    var storyFlow: Flow[Payload, Payload, StoryMat] = Flow[Payload]

    transformTasks.foreach(_.foreach(tt => {
      storyFlow = storyFlow.via(tt.flow())
    }))

    storyFlow = storyFlow.via(sinkTask.flow())

    fallbackTask.foreach(ft => {
      storyFlow = storyFlow.via(ft.flow())
    })

    storyFlow = storyFlow
      .map(pl => {
        getStoryMonitor(storyName).newEvent(pl._2).onProcessed(pl._1, pl._2)
        pl
      })
      .watchTermination() {
        case (mat, done) =>
          done.onComplete {
            case Success(_)  => getStoryMonitor(storyName).onCompleted()
            case Failure(ex) => getStoryMonitor(storyName).onTerminated(ex)
          }(runningContext.getExecutionContext())
          mat
      }

    storyFlow
  }

  def run()(implicit runningContext: TaskRunningContext): Future[Done] = runningFuture getOrElse {
    try {
      runningFuture = Some(sourceTask.run(combineStoryFlow()))
      runningFuture.get
    } catch {
      case ex: Throwable =>
        logger.error(s"Run story $storyName failed with error, $ex")
        shutdown()
        throw ex
    }
  }

  def shutdown()(implicit runningContext: TaskRunningContext): Unit =
    try {
      logger.info(s"Stopping story $storyName")
      runningFuture = None
      sourceTask.shutdown()
      transformTasks.foreach(_.foreach(_.shutdown()))
      fallbackTask.foreach(_.shutdown())
      sinkTask.shutdown()
    } catch {
      case NonFatal(ex) =>
        logger.error(s"Get an error when stopping story $storyName, $ex")
        throw ex
    }

}

object Story extends Logging {

  def wrapTaskFlow(
      taskFlow: Flow[Event, Payload, NotUsed]
  )(implicit runningContext: TaskRunningContext): Flow[Payload, Payload, NotUsed] =
    Flow
      .fromGraph(
        GraphDSL
          .create() { implicit builder =>
            import GraphDSL.Implicits._

            // NORM goes to 0, Others goes to 1
            val partitioner =
              builder.add(new Partition[Payload](2, {
                case (NORM, _) => 0
                case (_, _)    => 1
              }, false))
            val wrappedTaskFlow = Flow[Payload].map(_._2).via(taskFlow)
            val output          = builder.add(Merge[Payload](2))

            // format: off
            // ---------------  workflow graph start ----------------
            // NORM goes to taskHandler >>>
            partitioner.out(0)   ~>   wrappedTaskFlow   ~>   output.in(0)
            // Other status will skip this task flow >>>
            partitioner.out(1)              ~>               output.in(1)
            // ---------------  workflow graph end ----------------
            // format: on

            // ports
            FlowShape(partitioner.in, output.out)
          }
      )

  def wrapTask(
      taskHandler: Flow[Payload, Payload, NotUsed],
      taskName: String,
      fallbackTask: Option[IFallbackTask] = None,
      skipPreCheck: Boolean = false
  )(implicit runningContext: TaskRunningContext): Flow[Payload, Payload, NotUsed] =
    Flow
      .fromGraph(
        GraphDSL
          .create() { implicit builder =>
            import GraphDSL.Implicits._

            // SkipPreCheck goes to 0, NORM goes to 0, Others goes to 1
            val preCheck =
              builder.add(new Partition[Payload](2, input => {
                if (skipPreCheck) 0
                else {
                  input match {
                    case (NORM, _) => 0
                    case (_, _)    => 1
                  }
                }
              }))

            // TOFB goes to 1, Others goes to 0
            val postCheck =
              builder.add(Partition[Payload](2, {
                case (_: TOFB, _) => 1
                case (_, _)       => 0
              }))

            val output = builder.add(Merge[Payload](3))

            val fallback = Flow[Payload]
              .map {
                case input @ (_, event) =>
                  val logMessage =
                    s"Event ${event.uuid} was processing failed on task: $taskName." +
                      (if (fallbackTask.isDefined) " Sending to fallbackTask." else "")
                  logger.warn(logMessage)
                  input
              }
              .via(
                fallbackTask
                  .map(_task => Flow[Payload].via(_task.flow()))
                  .getOrElse(Flow[Payload])
              )

            val finalTaskHandler: Flow[Payload, Payload, NotUsed] = if (runningContext.getAppContext().isDev()) {
              taskHandler.via(Flow[Payload].map(pl => {
                logger.debug(s"The task $taskName has returned $pl")
                pl
              }))
            } else {
              taskHandler
            }

            // format: off
            // ---------------  workflow graph start ----------------


            // NORM goes to taskHandler >>>
            preCheck.out(0)   ~>   finalTaskHandler   ~>   postCheck
                                                           // non-TOFB goes to next task
                                                           postCheck.out(0)            ~>              output.in(0)
                                                           // TOFB goes to fallback  >>>
                                                           postCheck.out(1) ~>      fallback      ~>   output.in(1)

            // Other status will skip this task >>>
            preCheck.out(1)                                       ~>                                   output.in(2)


            // ---------------  workflow graph end ----------------
            // format: on

            // ports
            FlowShape(preCheck.in, output.out)
          }
      )
      .named(taskName)
}

object StoryStatus extends Enumeration {
  type StoryStatus = Value

  val INIT      = Value(1, "INIT")
  val DEPLOYING = Value(2, "DEPLOYING")
  val RUNNING   = Value(3, "RUNNING")
  val FAILED    = Value(4, "FAILED")
  val STOPPING  = Value(5, "STOPPING")
  val STOPPED   = Value(6, "STOPPED")

  def apply(status: String): StoryStatus = status.toUpperCase match {
    case "DEPLOYING" => DEPLOYING
    case "RUNNING"   => RUNNING
    case "FAILED"    => FAILED
    case "STOPPING"  => STOPPING
    case "STOPPED"   => STOPPED
    case _           => INIT
  }
}
