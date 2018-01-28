import java.text.SimpleDateFormat
import java.util.Date

import Dependencies._

import scala.util.Try

lazy val commonSettings = Seq(
  organization := "com.thenetcircle",
  // version := "2.0.1-SNAPSHOT",
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.8"),
  crossVersion := CrossVersion.binary,
  scalacOptions ++= Seq("-unchecked", "-deprecation"),
  libraryDependencies ++= commonDependencies,
  scalafmtOnCompile := true,
  scalafmtVersion := "1.2.0"
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "event-bus"
  )
  .aggregate(core, admin)

lazy val core = (project in file("core"))
  .enablePlugins(JavaAppPackaging, BuildInfoPlugin)
  // .enablePlugins(SbtAspectj)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= coreDependencies,
    // bashScriptExtraDefines += s"""addJava "${(aspectjWeaverOptions in Aspectj).value.mkString(" ")}"""",
    buildInfoPackage := "com.thenetcircle.event_bus",
    buildInfoObject := "BuildInfo",
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion,
      BuildInfoKey.action("date")(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())),
      BuildInfoKey.action("commit")(Try(Process("git rev-parse HEAD").!!.stripLineEnd).getOrElse("?"))
    )
  )

lazy val admin = (project in file("admin/backend"))
  .enablePlugins(JavaAppPackaging)
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val integrationTest = (project in file("integration-test"))
// .enablePlugins(SbtAspectj)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= integrationTestDependencies,
    parallelExecution := false
    /* fork in Test := true,
    javaOptions in Test ++= Seq((aspectjWeaverOptions in Aspectj).value.mkString(" ")) */
  )
  .dependsOn(admin, core)
