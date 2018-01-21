package com.thenetcircle.event_bus.tasks.cassandra

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.datastax.driver.core._
import com.thenetcircle.event_bus.context.TaskRunningContext
import com.thenetcircle.event_bus.interfaces.EventStatus.{Fail, Norm, ToFB}
import com.thenetcircle.event_bus.interfaces.{Event, FallbackTask}
import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

case class CassandraSettings(contactPoints: List[String], port: Int = 9042, parallelism: Int = 2)

class CassandraFallback(settings: CassandraSettings) extends FallbackTask with StrictLogging {

  var cluster: Option[Cluster] = None
  var session: Option[Session] = None

  def init(keyspace: String): Unit = if (session.isEmpty) {
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

    val keyspace = s"eventbus_${runningContext.getAppContext().getAppName()}"

    implicit val system: ActorSystem = runningContext.getActorSystem()
    implicit val materializer: Materializer = runningContext.getMaterializer()
    implicit val executionContext: ExecutionContext = runningContext.getExecutionContext()

    init(keyspace)

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
    session.foreach(_.close())
    cluster.foreach(_.close())
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
