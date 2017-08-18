lazy val commonSettings = Seq(
  scalaVersion := "2.12.2"
)

lazy val eventBus = (project in file("."))
  .enablePlugins(GitVersioning)
  .settings(
    name := "event-bus",
    commonSettings,
    libraryDependencies ++= Dependencies.rootDeps
  )
