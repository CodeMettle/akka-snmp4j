/*
 * Deps.scala
 *
 * Updated: Dec 3, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */

import sbt._

object Deps {
    val snmp4jRepo = "snmp4j repo" at "https://oosnmp.net/dist/release"

    val akkaActor = "com.typesafe.akka" %% "akka-actor" % Versions.akka
    val akkaSlf = "com.typesafe.akka" %% "akka-slf4j" % Versions.akka
    val akkaTest = "com.typesafe.akka" %% "akka-testkit" % Versions.akka
    val logback = "ch.qos.logback" % "logback-classic" % Versions.logback
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest
    val snmp4j = "org.snmp4j" % "snmp4j" % Versions.snmp4j
    val sprayUtil = "io.spray" %% "spray-util" % Versions.spray

    val ficus2_10 = "net.ceedubs" %% "ficus" % Versions.ficus2_10
    val ficus2_11 = "net.ceedubs" %% "ficus" % Versions.ficus2_11
}
