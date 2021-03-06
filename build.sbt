import com.lightbend.lagom.core.LagomVersion

name := "Panakeia"

version := "0.1"

maintainer in ThisBuild := "panakeia@heilpraktiker-elbmarsch.de"
organization in ThisBuild := "de.heilpraktiker-elbmarsch"
//https://github.com/lagom/lagom-samples/blob/1.5.x/shopping-cart/shopping-cart-scala/build.sbt

scalaVersion := "2.13.2"
scalaVersion in ThisBuild := "2.13.2"

lagomServicesPortRange in ThisBuild := PortRange(40000, 45000)


val postgresDriver             = "org.postgresql"               % "postgresql"                                     % "42.2.8"
val macwire                    = "com.softwaremill.macwire"     %% "macros"                                        % "2.3.3" % "provided"
val scalaTest                  = "org.scalatest"                %% "scalatest"                                     % "3.0.8" % Test
//val akkaDiscoveryKubernetesApi = "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api"                 % "1.0.6"
val lagomScaladslAkkaDiscovery = "com.lightbend.lagom"          %% "lagom-scaladsl-akka-discovery-service-locator" % LagomVersion.current
val playJsonDerivedCodecs      = "org.julienrf"                 %% "play-json-derived-codecs"                      % "7.0.0"
val swaggerAnnotations = "io.swagger.core.v3" % "swagger-annotations" % "2.0.7"

val lagomOpenapiVersion = "1.1.0"
val lagomOpenapiApi = "org.taymyr.lagom" %% "lagom-openapi-scala-api" % lagomOpenapiVersion
val lagomOpenapiImpl = "org.taymyr.lagom" %% "lagom-openapi-scala-impl" % lagomOpenapiVersion

val elastic4sVersion      = "7.6.1"
val elastic4sDep = Seq(
  "com.sksamuel.elastic4s" %% "elastic4s-core" % elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-http-streams" % elastic4sVersion,
  "com.sksamuel.elastic4s" %% "elastic4s-testkit" % elastic4sVersion % "test"
)

ThisBuild / scalacOptions ++= List("-encoding", "utf8", "-deprecation", "-feature", "-unchecked", "-Xfatal-warnings")

//TODO einbauen damit die CI das darüber bauen kann!!!!
def dockerSettings = Seq(
  dockerUpdateLatest := true,
  dockerBaseImage := "adoptopenjdk/openjdk8",
  dockerUsername := sys.props.get("docker.username"),
  dockerRepository := sys.props.get("docker.registry"),
  maintainer := "panakeia@heilpraktiker-elbmarsch.de" //für dist
)

val cleanItAll = true

lagomCassandraCleanOnStart in ThisBuild := cleanItAll
lagomKafkaCleanOnStart in ThisBuild := cleanItAll
lagomCassandraEnabled in ThisBuild := false
lagomKafkaEnabled in ThisBuild := false
//lagomKafkaAddress in ThisBuild := "localhost"
lagomKafkaPort in ThisBuild := 10000
//https://itnext.io/how-to-install-kafka-using-docker-a2b7c746cbdc
//docker network create kafka-net --driver bridge
//docker run --name zookeeper-server -d -p 2181:2181 --network kafka-net -e ALLOW_ANONYMOUS_LOGIN=yes bitnami/zookeeper:latest
//docker run --name kafka-server1 --hostname localhost -d --network kafka-net -e auto.create.topics.enable=true -e ALLOW_PLAINTEXT_LISTENER=yes -e KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper-server:2181 -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:10000 -p 10000:9092 bitnami/kafka:latest


lazy val `panakeia` = (project in file("."))
  .aggregate(
    `security-api`, `security-impl`,
    `binary-management-api`,`binary-management-impl`,
    `benefitRate-management-api`, `benefitRate-management-impl`,
    `patient-management-api`,`patient-management-impl`,
    `file-management-api`,`file-management-impl`
  )



lazy val `security-api` = (project in file("security-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslServer % Optional,
      playJsonDerivedCodecs,
      "org.pac4j" %% "lagom-pac4j" % "2.1.0",
      "org.pac4j" % "pac4j-http" % "3.8.3",
      "org.pac4j" % "pac4j-jwt" % "3.8.3",
      "com.iheart" %% "ficus" % "1.4.7",
      swaggerAnnotations,
      lagomOpenapiApi
    ) ++ elastic4sDep
  )
  .settings(dockerSettings: _*)
  .dependsOn(`util`)

lazy val `security-impl` = (project in file("security-impl"))
  .enablePlugins(LagomScala,LauncherJarPlugin)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceJdbc,
      lagomScaladslTestKit,
      lagomScaladslKafkaBroker,
      macwire,
      scalaTest,
      lagomScaladslAkkaDiscovery,
//      akkaDiscoveryKubernetesApi,
      postgresDriver,
      lagomOpenapiImpl
    )
  )
  .settings(dockerSettings: _*)
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`security-api`)

//Datenverwaltung für binär daten
lazy val `binary-management-api` = (project in file("binary-management-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslServer,
      playJsonDerivedCodecs
    ) ++ elastic4sDep
  )
  .dependsOn(`util`)


lazy val `binary-management-impl` = (project in file("binary-management-impl"))
  .enablePlugins(LagomScala,LauncherJarPlugin)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceJdbc,
      lagomScaladslTestKit,
      lagomScaladslKafkaBroker,
      macwire,
      scalaTest,
      playJsonDerivedCodecs,
      lagomScaladslAkkaDiscovery,
      "io.minio" % "minio" % "7.0.2",
      postgresDriver
    )
  )
  .settings(dockerSettings: _*)
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`binary-management-api`,`security-api`)

lazy val `patient-management-api` = (project in file("patient-management-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslServer,
      playJsonDerivedCodecs
    )// ++ elastic4sDep //Sehe ich noch nicht also wirklich nötig
  )
  .dependsOn(`util`)

lazy val `patient-management-impl` = (project in file("patient-management-impl"))
  .enablePlugins(LagomScala,LauncherJarPlugin)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceJdbc,
      lagomScaladslTestKit,
      lagomScaladslKafkaBroker,
      macwire,
      scalaTest,
      playJsonDerivedCodecs,
      lagomScaladslAkkaDiscovery,
      postgresDriver
    )
  )
  .settings(dockerSettings: _*)
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`patient-management-api`,`security-api`)

//Patienten Akte
lazy val `file-management-api` = (project in file("file-management-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslServer,
      playJsonDerivedCodecs
    )// ++ elastic4sDep //Sehe ich noch nicht also wirklich nötig
  )
  .dependsOn(`util`)

lazy val `file-management-impl` = (project in file("file-management-impl"))
  .enablePlugins(LagomScala,LauncherJarPlugin)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceJdbc,
      lagomScaladslTestKit,
      lagomScaladslKafkaBroker,
      macwire,
      scalaTest,
      playJsonDerivedCodecs,
      lagomScaladslAkkaDiscovery,
      postgresDriver
    )
  )
  .settings(dockerSettings: _*)
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`file-management-api`,`security-api`,`binary-management-api`,`patient-management-api`)


lazy val `benefitRate-management-api` = (project in file("benefitRate-management-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslServer,
      playJsonDerivedCodecs
    )// ++ elastic4sDep //Sehe ich noch nicht also wirklich nötig
  )
  .dependsOn(`util`)

lazy val `benefitRate-management-impl` = (project in file("benefitRate-management-impl"))
  .enablePlugins(LagomScala,LauncherJarPlugin)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceJdbc,
      lagomScaladslTestKit,
      lagomScaladslKafkaBroker,
      macwire,
      scalaTest,
      playJsonDerivedCodecs,
      lagomScaladslAkkaDiscovery,
      postgresDriver
    )
  )
  .settings(dockerSettings: _*)
  .settings(lagomForkedTestSettings: _*)
  .dependsOn(`benefitRate-management-api`,`security-api`)


lazy val `util` = (project in file("util"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
//      lagomScaladslServer % Optional,
      playJsonDerivedCodecs,
      scalaTest,
      "com.vladsch.flexmark" % "flexmark-all" % "0.50.48",
      "com.googlecode.libphonenumber" % "libphonenumber" % "8.11.2",
      "org.typelevel"  %% "squants"  % "1.6.0", //https://github.com/typelevel/squants
      "com.github.t3hnar" %% "scala-bcrypt" % "4.1",
      "com.x5dev" % "chunk-templates" % "3.5.0",
      "com.github.dwickern" % "scala-nameof_2.12" % "1.0.3",
      "com.beachape" %% "enumeratum" % "1.5.15",
      "org.apache.commons" % "commons-lang3" % "3.9",
      "com.github.t3hnar" %% "scala-bcrypt" % "4.1",
      "com.iheart" %% "ficus" % "1.4.7",
      "joda-time" % "joda-time" % "2.10.6",
      "com.github.tototoshi" %% "scala-csv" % "1.3.6"
    )
  )