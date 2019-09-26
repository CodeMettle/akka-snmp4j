import SonatypeKeys._

// Metadata

organization := "com.codemettle.akka-snmp4j"

name := "akka-snmp4j"

description := "Library to aid usage of SNMP4J in Scala + Akka"

startYear := Some(2014)

homepage := Some(url("https://github.com/CodeMettle/akka-snmp4j"))

organizationName := "CodeMettle, LLC"

organizationHomepage := Some(url("http://www.codemettle.com"))

licenses += ("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))

scmInfo := Some(
  ScmInfo(url("https://github.com/CodeMettle/akka-snmp4j"), "scm:git:https://github.com/CodeMettle/akka-snmp4j.git",
    Some("scm:git:git@github.com:CodeMettle/akka-snmp4j.git")))

pomExtra := {
  <developers>
    <developer>
      <name>Steven Scott</name>
      <email>steven@codemettle.com</email>
      <url>https://github.com/codingismy11to7/</url>
    </developer>
  </developers>
}

// Build

crossScalaVersions := Seq("2.11.7", "2.12.5", "2.13.1")

scalaVersion := crossScalaVersions.value.last

scalacOptions ++= Seq("-unchecked", "-feature", "-deprecation")

scalacOptions += {
  CrossVersion partialVersion scalaVersion.value match {
    case Some((x, y)) if x >= 2 && y >= 12 => "-target:jvm-1.8"
    case _ => "-target:jvm-1.6"
  }
}

resolvers += Deps.snmp4jRepo

libraryDependencies ++= Seq(
  Deps.akkaActor,
  Deps.snmp4j
) map (_ % Provided)

libraryDependencies ++= Seq(
  Deps.akkaSlf,
  Deps.akkaTest,
  Deps.logback,
  Deps.ficus,
  Deps.scalaTest
) map (_ % Test)

publishArtifact in Test := true

autoAPIMappings := true

apiMappings ++= {
  val cp: Seq[Attributed[File]] = (fullClasspath in Compile).value

  def findManagedDependency(moduleId: ModuleID): File = {
    (for {
      entry ← cp
      module ← entry.get(moduleID.key)
      if module.organization == moduleId.organization
      if module.name startsWith moduleId.name
      jarFile = entry.data
    } yield jarFile).head
  }

  Map(
    findManagedDependency("org.scala-lang" % "scala-library" % scalaVersion.value) → url(s"http://www.scala-lang.org/api/${scalaVersion.value}/"),
    findManagedDependency(Deps.akkaActor) → url(s"http://doc.akka.io/api/akka/${Versions.akka}/")
  )
}

// Release

releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseCrossBuild := true

// Publish

xerial.sbt.Sonatype.sonatypeSettings

profileName := "com.codemettle"
