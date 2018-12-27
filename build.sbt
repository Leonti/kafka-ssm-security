scalaVersion := "2.12.6"
name := "kafka-ssm-security"
organization := "com.myob"
version := "0.2"

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-language:higherKinds",
  "-Xfatal-warnings",
  "-Ywarn-dead-code",
  "-Ywarn-inaccessible",
  "-Ywarn-unused",
  "-Ywarn-unused-import",
  "-Ypartial-unification",
  "-encoding", "utf8"
)

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "1.4.0",
  "org.typelevel" %% "cats-effect" % "1.0.0",
  "com.amazonaws" % "aws-java-sdk-ssm" % "1.11.461",
  "com.amazonaws" % "aws-java-sdk-cloudwatch" % "1.11.461",
  "com.amazonaws" % "aws-java-sdk-logs" % "1.11.461",
  "org.apache.kafka" %% "kafka" % "2.0.0",
  "org.scalatest" %% "scalatest" % "3.0.4" % "it,test"
)

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
