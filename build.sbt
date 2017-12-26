import Dependencies._

def itFilter(name: String): Boolean = name endsWith "ISpec"
def unitFilter(name: String): Boolean = (name endsWith "Spec") && !itFilter(name)

lazy val eventBus = (project in file("."))
  .enablePlugins(JavaAppPackaging, SbtAspectj, BuildInfoPlugin)
  .settings(
    organization := "com.thenetcircle",
    name := "event-bus",
    scalaVersion := "2.12.2",
    libraryDependencies ++= mainDependencies,
    bashScriptExtraDefines += s"""addJava "${(aspectjWeaverOptions in Aspectj).value
      .mkString(" ")}""""
  )
  .configs(IntegrationTest)
  .settings(
    Defaults.itSettings,
    testOptions in Test := Seq(Tests.Filter(unitFilter)),
    testOptions in IntegrationTest := Seq(Tests.Filter(itFilter))
  )

lazy val stressTest = (project in file("stresstest"))
  .enablePlugins(GatlingPlugin)
  .settings(
    organization := "com.thenetcircle",
    name := "event-bus-stresstest",
    scalaVersion := "2.11.8",
    libraryDependencies ++= stressTestDependencies
  )
