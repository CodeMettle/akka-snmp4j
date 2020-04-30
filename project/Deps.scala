/*
 * Deps.scala
 *
 * Updated: Dec 3, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */

import sbt._

object Deps {
    val akkaActor = "com.typesafe.akka" %% "akka-actor" % Versions.akka
    val akkaStream = "com.typesafe.akka" %% "akka-stream" % Versions.akka
    val akkaSlf = "com.typesafe.akka" %% "akka-slf4j" % Versions.akka
    val akkaTest = "com.typesafe.akka" %% "akka-testkit" % Versions.akka
    val logback = "ch.qos.logback" % "logback-classic" % Versions.logback
    val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest
    val snmp4j = "org.snmp4j" % "snmp4j" % Versions.snmp4j

    val ficus = "com.iheart" %% "ficus" % Versions.ficus
}
