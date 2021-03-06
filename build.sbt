import java.text.SimpleDateFormat
import java.util.Date

import Dependencies._
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._

import scala.util.Try

lazy val commonSettings = Seq(
  organization := "com.thenetcircle",
  scalaVersion := "2.12.8",
  crossScalaVersions := Seq(scalaVersion.value, "2.11.12"),
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
    bashScriptExtraDefines += """addJava "-Dapp.admin.static_dir=$(dirname $0)/../dist""""
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

lazy val buildFrontend = taskKey[Unit]("Build admin frontend")
lazy val admin = (project in file("admin/backend"))
  .settings(commonSettings)
  /*.settings(
    buildFrontend := {
      val frontendHomeDir = baseDirectory.value / ".." / "frontend"
      def runNpmCommand(command: String) = {
        println(s"""Running command "npm $command" in directory $frontendHomeDir""")
        Process(s"npm $command", frontendHomeDir).!
      }

      if (runNpmCommand("install") != 0) throw new Exception("Npm install failed.")
      if (runNpmCommand("run build") != 0) throw new Exception("Npm build failed.")
    },
    compile in Compile := (compile in Compile).dependsOn(buildFrontend).value
  )*/
  .dependsOn(core)

lazy val integrationTest = (project in file("integration-test"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= integrationTestDependencies,
    parallelExecution := false
    // fork in Test := true
  )
  .dependsOn(admin, core)
