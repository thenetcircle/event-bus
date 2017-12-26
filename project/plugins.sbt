logLevel := sbt.Level.Info

resolvers += Resolver.sbtPluginRepo("releases")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-aspectj" % "0.11.0")
addSbtPlugin("io.gatling" % "gatling-sbt" % "2.2.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.7.0")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "2.0.0")
