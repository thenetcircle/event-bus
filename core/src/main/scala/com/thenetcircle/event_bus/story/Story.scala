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

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.scaladsl.Flow
import com.thenetcircle.event_bus.misc.Logging
import com.thenetcircle.event_bus.story.Story.OpExecPos
import com.thenetcircle.event_bus.story.Story.OpExecPos.{AfterSink, BeforeSink}
import com.thenetcircle.event_bus.story.StoryStatus.StoryStatus
import com.thenetcircle.event_bus.story.interfaces._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

case class StorySettings(name: String, settings: String)

class Story(
    val settings: StorySettings,
    val source: ISource,
    val sink: ISink,
    val operators: Option[List[(OpExecPos, IOperator)]] = None
) extends Logging {

  // initialize internal status
  val storyName: String                           = settings.name
  private var storyStatus: StoryStatus            = StoryStatus.INIT
  private var runningFuture: Option[Future[Done]] = None

  private def getTaskClassName(t: ITask): String = Option(t.getClass.getSimpleName).getOrElse("")

  // initialize tasks
  source.initTask(s"story:$storyName#source:${getTaskClassName(source)}", this)
  sink.initTask(s"story:$storyName#sink:${getTaskClassName(sink)}", this)
  operators.foreach(_.zipWithIndex.foreach {
    case ((_, op), i) => op.initTask(s"story:$storyName#operator:$i:${getTaskClassName(op)}", this)
  })

  def updateStoryStatus(status: StoryStatus): Unit = storyStatus = status
  def getStoryStatus(): StoryStatus                = storyStatus

  def combineStoryFlow()(implicit runningContext: TaskRunningContext): Flow[Payload, Payload, StoryMat] = {
    var storyFlow: Flow[Payload, Payload, StoryMat] = sink.sinkFlow()

    operators.foreach(_.reverse.foreach {
      case (_, o: IBidiOperator)          => storyFlow = storyFlow.join(o.flow())
      case (BeforeSink, o: IUndiOperator) => storyFlow = o.flow().via(storyFlow)
      case (AfterSink, o: IUndiOperator)  => storyFlow = storyFlow.via(o.flow())
      case _                              =>
    })

    // connect monitor flow
    storyFlow = storyFlow
      .map {
        case pl @ (status, event) =>
          StoryMonitor(storyName).newEvent(event).onProcessed(status, event)
          pl
      }
      .watchTermination() {
        case (mat, done) =>
          done.onComplete {
            case Success(_)  => StoryMonitor(storyName).onCompleted()
            case Failure(ex) => StoryMonitor(storyName).onTerminated(ex)
          }(runningContext.getExecutionContext())
          mat
      }

    storyFlow
  }

  def run()(implicit runningContext: TaskRunningContext): Future[Done] = runningFuture getOrElse {
    try {
      runningFuture = Some(source.run(combineStoryFlow()))
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
      source.shutdown()
      operators.foreach(_.foreach(_._2.shutdown()))
      sink.shutdown()
    } catch {
      case NonFatal(ex) =>
        logger.error(s"Get an error when stopping story $storyName, $ex")
        throw ex
    }

}

object Story extends Logging {
  def props(story: Story, runner: ActorRef)(implicit runningContext: TaskRunningContext): Props =
    Props(classOf[StoryActor], story, runner, runningContext)

  object Commands {
    case object Shutdown
    case class Restart(cause: Throwable)
  }

  sealed trait OpExecPos
  object OpExecPos {
    def apply(op: Option[String]): OpExecPos = op.map(_.toLowerCase) match {
      case Some("after") => AfterSink
      case _             => BeforeSink
    }
    case object BeforeSink extends OpExecPos
    case object AfterSink  extends OpExecPos
  }

  class StoryActor(story: Story, runner: ActorRef)(implicit runningContext: TaskRunningContext)
      extends Actor
      with ActorLogging {

    import Story.Commands._

    val storyName: String = story.storyName

    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    override def preStart(): Unit = {
      log.info(s"Starting the StoryActor of story $storyName")
      val doneFuture = story.run()
      // fix the case if this actor is dead already, the Shutdown commend will send to dead-letter
      val selfPath = self.path

      doneFuture.onComplete {
        case Success(_) =>
          log.warning(s"The story $storyName is complete, clean up now.")
          try {
            context.actorSelection(selfPath) ! Shutdown
          } catch {
            case ex: Throwable =>
          }

        case Failure(ex) =>
          log.warning(s"The story $storyName was running failed with error $ex, clean up now.")
          try {
            context.actorSelection(selfPath) ! Restart(ex)
          } catch {
            case ex: Throwable =>
          }
      }
    }

    override def postStop(): Unit = {
      log.warning(s"Stopping the StoryActor of story $storyName")
      story.shutdown()
    }

    override def receive: Receive = {
      case Shutdown =>
        log.info(s"Shutting down the StoryActor of story $storyName")
        context.stop(self)

      case Restart(ex) =>
        log.info(s"Restarting the StoryActor of story $storyName")
        throw ex
    }
  }
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
