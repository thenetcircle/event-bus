lazy val eventBus = (project in file("."))
  .enablePlugins(GitVersioning)
  .configs(IntegrationTest)
  .settings(
    name := "event-bus",
    scalaVersion := "2.12.2",
    Defaults.itSettings,
    libraryDependencies ++= Dependencies.eventBusDeps
  )

lazy val benchmark = (project in file("benchmark"))
  .enablePlugins(GatlingPlugin)
  .settings(
    name := "event-bus-benchmark",
    scalaVersion := "2.11.8",
    scalacOptions := Seq("-encoding",
                         "UTF-8",
                         "-target:jvm-1.8",
                         "-deprecation",
                         "-feature",
                         "-unchecked",
                         "-language:implicitConversions",
                         "-language:postfixOps"),
    libraryDependencies ++= Dependencies.benchmarkDeps
  )
