import sbt._

object Dependencies {
  private val akkaVersion = "2.5.4"
  private val akkaHttpVersion = "10.0.9"

  // Dependencies
  private val akka = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  private val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
  private val akkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test
  private val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  private val akkaHttpTestkit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
  private val akkaStreamKafka = "com.typesafe.akka" %% "akka-stream-kafka" % "0.17"
  private val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2"

  private val curator = "org.apache.curator" % "curator-recipes" % "4.0.0"
  private val sprayJson = "io.spray" %% "spray-json" % "1.3.3"
  private val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  private val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % Test

  private val kamonVersion = "0.6.7"
  private val kamonCore = "io.kamon" %% "kamon-core" % kamonVersion
  private val kamonAkka = "io.kamon" %% "kamon-akka-2.5" % kamonVersion
  private val kamonStatsd = "io.kamon" %% "kamon-statsd" % kamonVersion
  private val kamonLogReporter = "io.kamon" %% "kamon-log-reporter" % kamonVersion
  private val kamonSystemMetrics = "io.kamon" %% "kamon-system-metrics" % kamonVersion

  private val scalaUUID = "io.jvm.uuid" %% "scala-uuid" % "0.2.3"
  private val ficus = "com.iheart" %% "ficus" % "1.4.2"
  // private val akkaTracing = "com.github.levkhomich" %% "akka-tracing-core" % "0.6"
  // private val rediscala = "com.github.etaty"   %% "rediscala"                % "1.8.0"
  // private val akkaStreamAMQP  = "com.lightbend.akka" %% "akka-stream-alpakka-amqp" % "0.10"

  private val gatlingChartsHighcharts = "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.2.2" % Test
  private val gatlingTestFramework = "io.gatling" % "gatling-test-framework" % "2.2.2" % Test

  val coreDependencies = Seq(
    akka,
    akkaStream,
    akkaStreamTestkit,
    akkaHttp,
    akkaHttpTestkit,
    akkaStreamKafka,
    curator,
    sprayJson,
    scalaLogging,
    logback,
    scalaTest,
    scalaUUID,
    ficus,
    kamonCore,
    kamonAkka,
    kamonStatsd,
    kamonLogReporter,
    kamonSystemMetrics
  )

  val benchmarkDependencies = Seq(gatlingChartsHighcharts, gatlingTestFramework, akkaHttp)
}
