import Dependencies._

lazy val eventBus = (project in file("."))
  .enablePlugins(JavaAppPackaging, SbtAspectj, BuildInfoPlugin)
  .settings(
    organization := "com.thenetcircle",
    name := "event-bus",
    scalaVersion := "2.12.2",
    libraryDependencies ++= mainDependencies,
    bashScriptExtraDefines += s"""addJava "${(aspectjWeaverOptions in Aspectj).value.mkString(" ")}"""",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.thenetcircle.event_bus"
  )

lazy val stressTest = (project in file("stresstest"))
  .enablePlugins(GatlingPlugin)
  .settings(
    organization := "com.thenetcircle",
    name := "event-bus-stresstest",
    scalaVersion := "2.11.8",
    libraryDependencies ++= stressTestDependencies
  )
