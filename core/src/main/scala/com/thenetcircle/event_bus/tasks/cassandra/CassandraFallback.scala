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

package com.thenetcircle.event_bus.tasks.cassandra

import java.util.Date

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.datastax.driver.core._
import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture}
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.misc.Util
import com.thenetcircle.event_bus.interfaces.EventStatus.{Fail, InFB, ToFB}
import com.thenetcircle.event_bus.interfaces.{Event, EventStatus, FallbackTask, FallbackTaskBuilder}
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

case class CassandraSettings(contactPoints: List[String], port: Int = 9042, parallelism: Int = 2)

class CassandraFallback(val settings: CassandraSettings) extends FallbackTask with StrictLogging {

  private var clusterOption: Option[Cluster]             = None
  private var sessionOption: Option[Session]             = None
  private var statementOption: Option[PreparedStatement] = None

  def initializeCassandra(keyspace: String): Unit = if (sessionOption.isEmpty) {
    clusterOption = Some(
      Cluster
        .builder()
        .addContactPoints(settings.contactPoints: _*)
        .withPort(settings.port)
        .build()
    )
    sessionOption = clusterOption.map(_.connect(keyspace))
    sessionOption.foreach(s => { statementOption = Some(getPreparedStatement(s)) })

    val _session = sessionOption.get
    // create tables if not exists
    _session.execute(
      s"""create keyspace $keyspace if not exists
          | with replication = {'class': 'SimpleStrategy', 'replication_factor': '2'}  and durable_writes = true;""".stripMargin
    )

    _session.execute(s"""create table if not exists $keyspace.fallback(
                         |uuid text,
                         |storyname text,
                         |eventname text,
                         |createdat timestamp,
                         |fallbacktime timestamp,
                         |failedtaskname text,
                         |group text,
                         |providerid text,
                         |providertype text,
                         |generatorid text,
                         |generatortype text,
                         |actorid text,
                         |actortype text,
                         |targetid text,
                         |targettype text,
                         |body text,
                         |format text,
                         |cause text,
                         |primary key (uuid, storyname, createdat, group, eventname)
                         |);""".stripMargin)

    _session.execute(s"""CREATE MATERIALIZED VIEW IF NOT EXISTS $keyspace.fallback_by_storyname AS
                         | SELECT * FROM fallback WHERE uuid IS NOT NULL AND storyname IS NOT NULL AND createdat IS NOT NULL AND eventname IS NOT NULl AND group IS NOT NULL
                         | PRIMARY KEY (storyname, createdat, eventname, group, uuid)""".stripMargin)
  }

  override def prepareForTask(taskName: String)(
      implicit runningContext: TaskRunningContext
  ): Flow[(EventStatus, Event), (EventStatus, Event), NotUsed] = {

    val keyspace = s"event-bus-${runningContext.getAppContext().getAppName()}"

    implicit val system: ActorSystem                = runningContext.getActorSystem()
    implicit val materializer: Materializer         = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    initializeCassandra(keyspace)

    val session         = sessionOption.get
    val statementBinder = getStatementBinder(taskName)

    import GuavaFutures._

    Flow[(EventStatus, Event)]
      .mapAsync(settings.parallelism) {
        case input @ (_, event) ⇒
          try {
            session
              .executeAsync(statementBinder(input, statementOption.get))
              .asScala()
              .map[(EventStatus, Event)](result => (InFB, event))
              .recover {
                case NonFatal(ex) =>
                  logger.warn(s"sending to cassandra[1] fallback was failed with error $ex")
                  (Fail(ex), event)
              }
          } catch {
            case NonFatal(ex) =>
              logger.debug(s"sending to cassandra[2] fallback failed with error $ex")
              Future.successful((Fail(ex), event))
          }
      }
  }

  def getPreparedStatement(session: Session): PreparedStatement = {
    logger.debug(s"preparing statement ----")
    session.prepare(s"""
                       |INSERT INTO fallback
                       |(uuid, storyname, eventname, createdat, fallbacktime, failedtaskname, group, providerid, providertype, generatorid, generatortype, actorid, actortype, targetid, targettype, body, format, cause)
                       |VALUES
                       |(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                       |""".stripMargin)
  }

  def getStatementBinder(failedTaskName: String)(
      implicit runningContext: TaskRunningContext
  ): ((EventStatus, Event), PreparedStatement) => BoundStatement = {
    case ((status, event), statement) =>
      val cause =
        if (status.isInstanceOf[ToFB]) status.asInstanceOf[ToFB].cause.map(_.toString).getOrElse("")
        else ""
      logger.debug(s"binding statement ----")
      statement.bind(
        event.uuid,
        runningContext.getStoryName(),
        event.metadata.name.getOrElse(""),
        event.createdAt,
        new Date(),
        failedTaskName,
        event.metadata.group.getOrElse(""),
        event.metadata.provider.map(_._2).getOrElse(""),
        event.metadata.provider.map(_._1).getOrElse(""),
        event.metadata.generator.map(_._2).getOrElse(""),
        event.metadata.generator.map(_._1).getOrElse(""),
        event.metadata.actor.map(_._2).getOrElse(""),
        event.metadata.actor.map(_._1).getOrElse(""),
        event.metadata.target.map(_._2).getOrElse(""),
        event.metadata.target.map(_._1).getOrElse(""),
        event.body.data,
        event.body.format.toString,
        cause
      )
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    sessionOption.foreach(s => { s.close(); sessionOption = None })
    clusterOption.foreach(c => { c.close(); clusterOption = None })
  }
}

private[cassandra] object GuavaFutures {
  implicit final class GuavaFutureOpts[A](val guavaFut: ListenableFuture[A]) extends AnyVal {
    def asScala(): Future[A] = {
      val p = Promise[A]()
      val callback = new FutureCallback[A] {
        override def onSuccess(a: A): Unit           = p.success(a)
        override def onFailure(err: Throwable): Unit = p.failure(err)
      }
      Futures.addCallback(guavaFut, callback)
      p.future
    }
  }
}

class CassandraFallbackBuilder() extends FallbackTaskBuilder {

  override def build(
      configString: String
  )(implicit buildingContext: TaskBuildingContext): CassandraFallback = {
    val config = Util
      .convertJsonStringToConfig(configString)
      .withFallback(buildingContext.getSystemConfig().getConfig("task.cassandra-fallback"))

    val cassandraSettings = CassandraSettings(
      contactPoints = config.as[List[String]]("contact-points"),
      port = config.as[Int]("port"),
      parallelism = config.as[Int]("parallelism")
    )

    new CassandraFallback(cassandraSettings)
  }

}
