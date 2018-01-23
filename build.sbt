import Dependencies._

lazy val commonSettings = Seq(
    scalaVersion := "2.12.2",
    organization := "com.thenetcircle"
)

lazy val core = (project in file("core"))
  .enablePlugins(JavaAppPackaging, SbtAspectj)
  .settings(
    name := "event-bus-core",
    commonSettings,
    libraryDependencies ++= coreDependencies,
    // mainClass in Compile := Some("com.thenetcircle.event_bus.ZKBasedRunner"),
    bashScriptExtraDefines += s"""addJava "${(aspectjWeaverOptions in Aspectj).value.mkString(" ")}""""
  )

lazy val admin = (project in file("admin/backend"))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "event-bus-admin",
    commonSettings
  )
  .dependsOn(core % "compile")


lazy val integrationTest = (project in file("integrationtest"))
  .configs(IntegrationTest)
  .settings(
    name := "event-bus-integration-test",
    commonSettings,
    Defaults.itSettings
  )
  .dependsOn(core % "test")
