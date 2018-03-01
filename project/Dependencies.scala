import sbt._

object Dependencies {
  val akkaVersion      = "2.5.7"
  val akkaHttpVersion  = "10.0.11"
  val kamonVersion     = "0.6.7"
  val gatlingVersion   = "2.2.2"
  val zookeeperVersion = "3.4.11"

  // Dependencies
  val akka                = "com.typesafe.akka"          %% "akka-slf4j"                    % akkaVersion
  val akkaStream          = "com.typesafe.akka"          %% "akka-stream"                   % akkaVersion
  val akkaStreamTestkit   = "com.typesafe.akka"          %% "akka-stream-testkit"           % akkaVersion % Test
  val akkaHttp            = "com.typesafe.akka"          %% "akka-http"                     % akkaHttpVersion
  val akkaHttpTestkit     = "com.typesafe.akka"          %% "akka-http-testkit"             % akkaHttpVersion % Test
  val akkaHttpSprayJson   = "com.typesafe.akka"          %% "akka-http-spray-json"          % akkaHttpVersion
  val akkaStreamKafka     = "com.typesafe.akka"          %% "akka-stream-kafka"             % "0.18"
  val akkaStreamCassandra = "com.lightbend.akka"         %% "akka-stream-alpakka-cassandra" % "0.16"
  val scalaLogging        = "com.typesafe.scala-logging" %% "scala-logging"                 % "3.7.2"

  val curator   = "org.apache.curator"   % "curator-recipes" % "4.0.0" exclude ("org.apache.zookeeper", "zookeeper") // for zookeeper 3.4.x, needs to exclude original one
  val zookeeper = "org.apache.zookeeper" % "zookeeper"       % zookeeperVersion exclude ("org.slf4j", "slf4j-log4j12")

  // val sprayJson = "io.spray"       %% "spray-json"     % "1.3.3"
  val logback   = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val scalaTest = "org.scalatest"  %% "scalatest"      % "3.0.1" % Test

  val kamonCore          = "io.kamon"  %% "kamon-core"           % kamonVersion
  val kamonAkka          = "io.kamon"  %% "kamon-akka-2.5"       % kamonVersion
  val kamonStatsd        = "io.kamon"  %% "kamon-statsd"         % kamonVersion
  val kamonSystemMetrics = "io.kamon"  %% "kamon-system-metrics" % kamonVersion
  val kamonLogReporter   = "io.kamon"  %% "kamon-log-reporter"   % kamonVersion
  val sentry             = "io.sentry" % "sentry-logback"        % "1.6.6"

  val ficus = "com.iheart" %% "ficus" % "1.4.2"
  // val akkaTracing = "com.github.levkhomich" %% "akka-tracing-core" % "0.6"
  // val rediscala = "com.github.etaty"   %% "rediscala"                % "1.8.0"
  // val akkaStreamAMQP  = "com.lightbend.akka" %% "akka-stream-alpakka-amqp" % "0.10"

  val gatlingChartsHighcharts = "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion % Test
  val gatlingTestFramework    = "io.gatling"            % "gatling-test-framework"    % gatlingVersion % Test

  val commonDependencies = Seq(
    scalaLogging,
    logback,
    scalaTest
  )

  val coreDependencies = Seq(
    akka,
    akkaStream,
    akkaStreamTestkit,
    akkaHttp,
    akkaHttpTestkit,
    akkaHttpSprayJson,
    akkaStreamKafka,
    akkaStreamCassandra,
    curator,
    zookeeper,
    ficus,
    kamonCore,
    // kamonAkka,
    kamonStatsd,
    kamonSystemMetrics,
    kamonLogReporter,
    sentry
  )

  val integrationTestDependencies = Seq(
    akkaStreamTestkit,
    akkaHttpTestkit
  )
}
