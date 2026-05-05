name    := "power-cons-backend"
version := "1.0.0"

scalaVersion := "2.13.14"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      guice,
      jdbc,
      "org.postgresql"          %  "postgresql"                     % "42.7.3",
      "org.playframework.anorm" %% "anorm"                          % "2.7.0",
      "org.eclipse.paho"        %  "org.eclipse.paho.client.mqttv3" % "1.2.5",
    ),
    // suppress verbose sbt output
    logLevel := Level.Warn
  )