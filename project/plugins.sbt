logLevel := Level.Warn

addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.6.2")

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.7.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.22")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.2.0")