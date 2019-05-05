/*
 * CredOptions.scala
 *
 * Updated: Jan 7, 2018
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle.akkasnmp4j.config

import com.typesafe.config.Config

import com.codemettle.akkasnmp4j.util.SnmpAuthProtocol.SnmpAuthProtocol
import com.codemettle.akkasnmp4j.util.SnmpPrivacyProtocol.SnmpPrivacyProtocol
import com.codemettle.akkasnmp4j.util.SnmpSecurityLevel.SnmpSecurityLevel
import com.codemettle.akkasnmp4j.util.{SettingsCompanion, SnmpAuthProtocol, SnmpPrivacyProtocol, SnmpSecurityLevel, SnmpVersion}

case class CredOptions(version: SnmpVersion.Value = SnmpVersion.v1,
                       securityLevel: SnmpSecurityLevel = SnmpSecurityLevel.noAuthNoPriv,
                       authProtocol: Option[SnmpAuthProtocol] = None,
                       privacyProtocol: Option[SnmpPrivacyProtocol] = None,
                       authPassPhrase: String,
                       privacyPassPhrase: String,
                       user: String,
                       readCommunity: String, writeCommunity: String,
                       contextName: Option[String] = None)

object CredOptions extends SettingsCompanion[CredOptions]("akkasnmp4j.cred-defaults") {
  override def fromSubConfig(c: Config): CredOptions = {
    apply(
      c.getString("version").toLowerCase match {
        case "v1" ⇒ SnmpVersion.v1
        case "v2c" ⇒ SnmpVersion.v2c
        case "v3" ⇒ SnmpVersion.v3
        case _ ⇒ SnmpVersion.v1
      },
      c.getString("security-level").toLowerCase match {
        case "authnopriv" ⇒ SnmpSecurityLevel.authNoPriv
        case "authpriv" ⇒ SnmpSecurityLevel.authPriv
        case _ ⇒ SnmpSecurityLevel.noAuthNoPriv
      },
      c.getString("auth-protocol").toUpperCase match {
        case "SHA" ⇒ Some(SnmpAuthProtocol.SHA)
        case "MD5" ⇒ Some(SnmpAuthProtocol.MD5)
        case _ ⇒ None
      },
      c.getString("privacy-protocol").toUpperCase match {
        case "DES" ⇒ Some(SnmpPrivacyProtocol.DES)
        case "AES" ⇒ Some(SnmpPrivacyProtocol.AES)
        case "AES192" ⇒ Some(SnmpPrivacyProtocol.AES192)
        case "AES256" ⇒ Some(SnmpPrivacyProtocol.AES256)
        case "CISCO_AES256" ⇒ Some(SnmpPrivacyProtocol.CISCO_AES256)
        case _ ⇒ None
      },
      c getString "auth-passphrase",
      c getString "privacy-passphrase",
      c getString "user",
      c getString "read-community",
      c getString "write-community",
      Option(c getString "context-name").filter(_.trim.nonEmpty)
    )
  }
}
