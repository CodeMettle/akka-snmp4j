/*
 * GetOptions.scala
 *
 * Updated: Dec 3, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle.akkasnmp4j.config

import com.typesafe.config.Config

import com.codemettle.akkasnmp4j.util.SettingsCompanion

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
 * @author steven
 *
 */
case class GetOptions(port: Int, retries: Int, timeout: Option[FiniteDuration])

object GetOptions extends SettingsCompanion[GetOptions]("akkasnmp4j.get-defaults") {
    override def fromSubConfig(c: Config): GetOptions = {
        apply(
            c getInt "port",
            c getInt "retries",
            Option(c getString "timeout") map (Duration(_)) match {
                case Some(fd: FiniteDuration) ⇒ Some(fd)
                case _ ⇒ None
            }
        )
    }
}
