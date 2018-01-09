import Dependencies._

lazy val core = (project in file("core"))
  .enablePlugins(JavaAppPackaging, SbtAspectj, BuildInfoPlugin)
  .settings(
    organization := "com.thenetcircle",
    name := "event-bus",
    scalaVersion := "2.12.2",
    libraryDependencies ++= coreDependencies,
    bashScriptExtraDefines += s"""addJava "${(aspectjWeaverOptions in Aspectj).value.mkString(" ")}"""",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.thenetcircle.event_bus"
  )

lazy val benchmark = (project in file("benchmark"))
  .enablePlugins(GatlingPlugin)
  .settings(
    organization := "com.thenetcircle",
    name := "event-bus-benchmark",
    scalaVersion := "2.11.8",
    libraryDependencies ++= benchmarkDependencies
  )
