package com.thenetcircle.event_bus.tasks.cassandra

import akka.NotUsed
import akka.stream.alpakka.cassandra.scaladsl.CassandraSink
import akka.stream.scaladsl.Flow
import com.datastax.driver.core.{Cluster, PreparedStatement}
import com.thenetcircle.event_bus.context.TaskRunningContext
import com.thenetcircle.event_bus.interfaces.{Event, FallbackTask}

class CassandraFallback extends FallbackTask {

  override def getHandler(failedTaskName: String)(
      implicit runningContext: TaskRunningContext
  ): Flow[(Status, Event), (Status, Event), NotUsed] = {

    implicit val system = runningContext.getActorSystem()
    implicit val materializer = runningContext.getMaterializer()
    implicit val session =
      Cluster.builder().addContactPoint("127.0.0.1").withPort(9042).build().connect()

    val keyspaces = runningContext.getAppContext().getAppName()

    val preparedStatement = session.prepare(s"INSERT INTO $keyspaces.test(id) VALUES (?)")

    val statementBinder = (myInteger: Integer, statement: PreparedStatement) =>
      statement.bind(myInteger)

    val sink = CassandraSink[Integer](parallelism = 2, preparedStatement, statementBinder)

  }

}
