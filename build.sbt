import Dependencies._

lazy val eventBus = (project in file("."))
  .enablePlugins(GitVersioning)
  .configs(IntegrationTest)
  .settings(
    name := "event-bus",
    scalaVersion := "2.12.2",
    Defaults.itSettings,
    libraryDependencies ++= mainDependencies
  )

lazy val stressTest = (project in file("stresstest"))
  .enablePlugins(GatlingPlugin)
  .settings(
    name := "event-bus-stresstest",
    scalaVersion := "2.11.8",
    libraryDependencies ++= stressTestDependencies
  )
