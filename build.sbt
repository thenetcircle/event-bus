lazy val commonSettings = Seq(
  scalaVersion := "2.12.2"
)

lazy val eventDispatcher = (project in file("."))
  .settings(
    name := "event-dispatcher",
    version := "2.0.1",
    commonSettings,
    libraryDependencies ++= Dependencies.rootDeps
  )
