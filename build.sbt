import Dependencies._

val releaseVersion = "2.0-SNAPSHOT"

lazy val eventBus = (project in file("."))
  .enablePlugins(JavaAppPackaging, SbtAspectj)
  .configs(IntegrationTest)
  .settings(
    organization := "com.thenetcircle",
    name := "event-bus",
    version := releaseVersion,
    scalaVersion := "2.12.2",
    libraryDependencies ++= mainDependencies,
    Defaults.itSettings,
    javaOptions in Universal ++= (aspectjWeaverOptions in Aspectj).value
  )

lazy val stressTest = (project in file("stresstest"))
  .enablePlugins(GatlingPlugin)
  .settings(
    organization := "com.thenetcircle",
    name := "event-bus-stresstest",
    version := releaseVersion,
    scalaVersion := "2.11.8",
    libraryDependencies ++= stressTestDependencies
  )
