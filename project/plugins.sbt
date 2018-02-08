logLevel := sbt.Level.Info

resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
  url("http://dl.bintray.com/content/sbt/sbt-plugin-releases")
)(Resolver.ivyStylePatterns)

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.3")
addSbtPlugin("com.dwijnand"     % "sbt-dynver"          % "2.0.0")
addSbtPlugin("com.lucidchart"   % "sbt-scalafmt"        % "1.15")
addSbtPlugin("com.eed3si9n"     % "sbt-buildinfo"       % "0.7.0")
// addSbtPlugin("io.gatling" % "gatling-sbt" % "2.2.0")
// addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")
// addSbtPlugin("com.lightbend.sbt" % "sbt-aspectj" % "0.11.0")
