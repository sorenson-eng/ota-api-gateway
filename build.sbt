name := "api-gateway"
organization := "com.sorenson"
scalaVersion := "2.13.6"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-target:jvm-1.8")

libraryDependencies ++= {
  val akkaV = "2.6.14"
  val akkaHttpV = "10.2.4"
  val scalaTestV = "3.2.9"

  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaV % Test,
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    "org.scalatest"     %% "scalatest" % scalaTestV % Test,
    "de.heikoseeberger" %% "akka-http-circe" % "1.36.0" % Test,
    "io.circe" %% "circe-core" % "0.14.0" % Test,
    "io.circe" %% "circe-generic" % "0.14.0" % Test,

    "ch.qos.logback" % "logback-classic" % "1.2.3"
  )
}

enablePlugins(BuildInfoPlugin, GitVersioning, JavaAppPackaging)

buildInfoOptions += BuildInfoOption.ToMap

buildInfoOptions += BuildInfoOption.BuildTime

import com.typesafe.sbt.packager.docker._

dockerRepository := Some("sorenson")

dockerUpdateLatest := true

dockerAliases ++= Seq(dockerAlias.value.withTag(git.gitHeadCommit.value))

Docker / defaultLinuxInstallLocation := s"/opt/${moduleName.value}"

dockerExposedPorts += 9001

dockerCommands := Seq(
  Cmd("FROM", "adoptopenjdk/openjdk8:jre8u292-b10-alpine"),
  ExecCmd("RUN", "apk", "add", "bash", "coreutils"),
  ExecCmd("RUN", "mkdir", "-p", s"/var/log/${moduleName.value}"),
  Cmd("ADD", "opt /opt"),
  Cmd("WORKDIR", s"/opt/${moduleName.value}"),
  ExecCmd("ENTRYPOINT", s"/opt/${moduleName.value}/bin/${moduleName.value}"),
  Cmd("RUN", s"chown -R daemon:daemon /opt/${moduleName.value}"),
  Cmd("RUN", s"chown -R daemon:daemon /var/log/${moduleName.value}"),
  Cmd("USER", "daemon")
)

