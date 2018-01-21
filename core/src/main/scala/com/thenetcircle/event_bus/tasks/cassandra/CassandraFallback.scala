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

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.datastax.driver.core._
import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture}
import com.thenetcircle.event_bus.context.{TaskBuildingContext, TaskRunningContext}
import com.thenetcircle.event_bus.helper.ConfigStringParser
import com.thenetcircle.event_bus.interfaces.EventStatus.{Fail, Norm, ToFB}
import com.thenetcircle.event_bus.interfaces.{Event, FallbackTask, FallbackTaskBuilder}
import com.typesafe.scalalogging.StrictLogging
import net.ceedubs.ficus.Ficus._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

case class CassandraSettings(contactPoints: List[String], port: Int = 9042, parallelism: Int = 2)

class CassandraFallback(settings: CassandraSettings) extends FallbackTask with StrictLogging {

  var cluster: Option[Cluster] = None
  var session: Option[Session] = None

  def initializeCassandra(keyspace: String): Unit = if (session.isEmpty) {
    cluster = Some(
      Cluster
        .builder()
        .addContactPoints(settings.contactPoints: _*)
        .withPort(settings.port)
        .build()
    )
    session = cluster.map(_.connect(keyspace))

    // create tables if not exists
    // session.execute("")
  }

  override def getHandler(failedTaskName: String)(
      implicit runningContext: TaskRunningContext
  ): Flow[(Status, Event), (Status, Event), NotUsed] = {

    // val keyspace = s"eventbus_${runningContext.getAppContext().getAppName()}"

    val keyspace = s"eventbus_test"

    implicit val system: ActorSystem = runningContext.getActorSystem()
    implicit val materializer: Materializer = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    initializeCassandra(keyspace)

    val _session = session.get
    val statement = getPreparedStatement(_session)
    val statementBinder = getStatementBinder(failedTaskName)

    import GuavaFutures._

    Flow[(Status, Event)]
      .mapAsyncUnordered(settings.parallelism) {
        case input @ (status, event) ⇒
          _session
            .executeAsync(statementBinder(input, statement))
            .asScala()
            .map[(Status, Event)](result => (Norm, event))
            .recover {
              case NonFatal(ex) =>
                logger.warn(s"sending to cassandra fallback was failed with error $ex")
                (Fail(ex), event)
            }
      }

  }

  def getPreparedStatement(session: Session): PreparedStatement = session.prepare(
    s"INSERT INTO eventbus_test VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
  )

  def getStatementBinder(failedTaskName: String)(
      implicit runningContext: TaskRunningContext
  ): ((Status, Event), PreparedStatement) => BoundStatement = {
    case ((status, event), statement) =>
      val cause =
        if (status.isInstanceOf[ToFB]) status.asInstanceOf[ToFB].cause.map(_.toString).getOrElse("")
        else ""
      statement.bind(
        event.uuid,
        runningContext.getStoryName(),
        event.metadata.name.getOrElse(""),
        long2Long(event.createdAt.getTime),
        long2Long(System.currentTimeMillis),
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
        event.body.format,
        cause
      )
  }

  override def shutdown()(implicit runningContext: TaskRunningContext): Unit = {
    session.foreach(s => { s.close(); session = None })
    cluster.foreach(c => { c.close(); cluster = None })
  }
}

private[cassandra] object GuavaFutures {
  implicit final class GuavaFutureOpts[A](val guavaFut: ListenableFuture[A]) extends AnyVal {
    def asScala(): Future[A] = {
      val p = Promise[A]()
      val callback = new FutureCallback[A] {
        override def onSuccess(a: A): Unit = p.success(a)
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
    val config = ConfigStringParser
      .convertStringToConfig(configString)
      .withFallback(buildingContext.getSystemConfig().getConfig("task.cassandra-fallback"))

    val cassandraSettings = CassandraSettings(
      contactPoints = config.as[List[String]]("contact-points"),
      port = config.as[Int]("port"),
      parallelism = config.as[Int]("parallelism")
    )

    new CassandraFallback(cassandraSettings)
  }

}
