lazy val eventBus = (project in file("."))
  .enablePlugins(GitVersioning)
  .configs(IntegrationTest)
  .settings(
    name := "event-bus",
    scalaVersion := "2.12.2",
    Defaults.itSettings,
    libraryDependencies ++= Dependencies.eventBusDeps
  )
