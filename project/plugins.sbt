logLevel := Level.Warn

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.2.0")
addSbtPlugin("io.gatling"       % "gatling-sbt"         % "2.2.0")

resolvers += Resolver.bintrayIvyRepo("sbt", "sbt-aspectj")
addSbtPlugin("com.lightbend.sbt" % "sbt-aspectj" % "0.11.0")
