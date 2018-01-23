import Dependencies._

lazy val core = (project in file("core"))
  .enablePlugins(JavaAppPackaging, SbtAspectj)
  .settings(
    organization := "com.thenetcircle",
    name := "event-bus",
    scalaVersion := "2.12.2",
    libraryDependencies ++= coreDependencies,
    mainClass in Compile := Some("com.thenetcircle.event_bus.ZKRunnerApp"),
    bashScriptExtraDefines += s"""addJava "${(aspectjWeaverOptions in Aspectj).value.mkString(" ")}""""
  )

lazy val benchmark = (project in file("benchmark"))
  .enablePlugins(GatlingPlugin)
  .settings(
    organization := "com.thenetcircle",
    name := "event-bus-benchmark",
    scalaVersion := "2.11.8",
    libraryDependencies ++= benchmarkDependencies
  )
