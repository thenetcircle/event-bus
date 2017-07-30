import sbt._

object Dependencies {
  // http://www.scala-sbt.org/0.13/docs/Library-Management.html
  // Versions
  lazy val akkaVersion = "2.4.12"

  // Libraries
  private val akkaBase = Seq(
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
  )
  private val log = Seq(
    "ch.qos.logback" % "logback-core" % "1.2.2",
    "ch.qos.logback" % "logback-classic" % "1.2.3"
  )
  private val test = Seq(
    "org.scalatest" %% "scalatest" % "3.0.1" % Test
  )

  private val base = akkaBase ++ log ++ test

  private val redisConnector = "com.github.etaty" %% "rediscala" % "1.8.0"
  private val amqpConnector  = "com.lightbend.akka" %% "akka-stream-alpakka-amqp" % "0.10"
  private val kafkaConnector = "com.typesafe.akka" %% "akka-stream-kafka" % "0.16"

  // Apps
  val rootDeps = base ++ Seq(
    redisConnector,
    amqpConnector,
    kafkaConnector
  )
}
