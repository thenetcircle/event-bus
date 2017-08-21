import sbt._

object Dependencies {
  // http://www.scala-sbt.org/0.13/docs/Library-Management.html
  // Versions
  lazy val akkaVersion     = "2.5.4"
  lazy val akkaHttpVersion = "10.0.9"

  // Libraries
  private val akkaStream = Seq(
    "com.typesafe.akka" %% "akka-stream" % akkaVersion
  )
  private val log = Seq(
    "com.typesafe.akka"          %% "akka-slf4j"     % akkaVersion,
    "com.typesafe.scala-logging" %% "scala-logging"  % "3.7.2",
    "ch.qos.logback"             % "logback-core"    % "1.2.2",
    "ch.qos.logback"             % "logback-classic" % "1.2.3"
  )
  private val test = Seq(
    "org.scalatest"     %% "scalatest"           % "3.0.1"         % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion     % Test,
    "com.typesafe.akka" %% "akka-http-testkit"   % akkaHttpVersion % Test
  )

  private val misc = Seq(
    "io.spray"    %% "spray-json" % "1.3.3",
    "io.jvm.uuid" %% "scala-uuid" % "0.2.3"
  )

  private val redisConnector = "com.github.etaty"   %% "rediscala"                % "1.8.0"
  private val httpConnector  = "com.typesafe.akka"  %% "akka-http"                % akkaHttpVersion
  private val amqpConnector  = "com.lightbend.akka" %% "akka-stream-alpakka-amqp" % "0.10"
  private val kafkaConnector = "com.typesafe.akka"  %% "akka-stream-kafka"        % "0.16"

  // Apps
  val eventBusDeps = akkaStream ++ log ++ test ++ misc ++ Seq(
    redisConnector,
    amqpConnector,
    kafkaConnector,
    httpConnector
  )
}
