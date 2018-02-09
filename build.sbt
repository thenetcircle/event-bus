import java.text.SimpleDateFormat
import java.util.Date

import Dependencies._
import NativePackagerHelper._

import scala.util.Try

lazy val commonSettings = Seq(
  organization := "com.thenetcircle",
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.8"),
  crossVersion := CrossVersion.binary,
  scalacOptions ++= Seq("-unchecked", "-deprecation"),
  libraryDependencies ++= commonDependencies,
  scalafmtOnCompile := true,
  scalafmtVersion := "1.2.0"
)

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(commonSettings)
  .settings(
    maintainer := "Baineng Ma <baineng.ma@gmail.com>",
    packageDescription := "A event distributing system stay among services",
    packageSummary := "A event distributing system stay among services",
    mappings in (Compile, packageDoc) := Seq(),
    name := "runner",
    mainClass in Compile := Some("com.thenetcircle.event_bus.Runner"),
    discoveredMainClasses in Compile := Seq("com.thenetcircle.event_bus.admin.Admin"),
    // integrate admin frontend static files
    mappings in Universal ++= directory("admin/frontend/dist"),
    bashScriptExtraDefines += """addJava "-Dapp.admin.static_dir=${app_home}/../dist""""
  )
  .aggregate(core, admin) // run commands on each sub project
  .dependsOn(core, admin) // does the actual aggregation

lazy val core = (project in file("core"))
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= coreDependencies,
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
  .settings(commonSettings)
  .dependsOn(core)

lazy val integrationTest = (project in file("integration-test"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= integrationTestDependencies,
    parallelExecution := false
    // fork in Test := true
  )
  .dependsOn(admin, core)
