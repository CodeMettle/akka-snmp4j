package com.codemettle.akkasnmp4j.util

import java.net.InetAddress

import org.snmp4j.mp.{MPv3, MessageProcessingModel, SnmpConstants}
import org.snmp4j.security._
import org.snmp4j.smi.{OctetString, UdpAddress}
import org.snmp4j.{CommunityTarget, Snmp, UserTarget, Target ⇒ snmpTarget}

import com.codemettle.akkasnmp4j.config.GetOptions

object Target {

  private def createCommunityTarget(addr: InetAddress, options: GetOptions): CommunityTarget = {
    val target = new CommunityTarget(new UdpAddress(addr, options.port), new OctetString(options.readCommunity))
    target setRetries options.retries
    options.timeout foreach (t ⇒ target setTimeout t.toMillis)
    target
  }

  private def createUserTarget(session: Snmp, addr: InetAddress, options: GetOptions): UserTarget = {

    def lookupV3EngineId(session: Snmp, target: snmpTarget): Array[Byte] = {
      val mpv3 = session.getMessageProcessingModel(MessageProcessingModel.MPv3).asInstanceOf[MPv3]
      val engineId = mpv3.getEngineID(target.getAddress)
      if (engineId == null) {
        session.getUSM.setEngineDiscoveryEnabled(true)
        session.discoverAuthoritativeEngineID(target.getAddress, target.getTimeout)
      } else
        engineId.toByteArray
    }

    val target = new UserTarget()
    target setRetries options.retries
    options.timeout.map(_.toMillis).foreach(target.setTimeout)

    val userName = new OctetString(options.user)
    target.setAddress(new UdpAddress(addr, options.port))
    target.setVersion(SnmpConstants.version3)
    target.setSecurityLevel(options.securityLevel.id)
    target.setSecurityName(userName)
    val authPassphrase = new OctetString(options.authPassPhrase)
    val privacyPassphrase = new OctetString(options.privacyPassPhrase)
    val authProtocolOid = options.authProtocol.map({
      case SnmpAuthProtocol.SHA ⇒ AuthSHA.ID
      case SnmpAuthProtocol.MD5 ⇒ AuthMD5.ID
    }).orNull
    val privacyProtocolOid = options.privacyProtocol.map({
      case SnmpPrivacyProtocol.DES ⇒ PrivDES.ID
      case SnmpPrivacyProtocol.AES ⇒ PrivAES128.ID
      case SnmpPrivacyProtocol.AES192 ⇒ PrivAES192.ID
      case SnmpPrivacyProtocol.AES256 ⇒ PrivAES256.ID
    }).orNull

    val engineId = lookupV3EngineId(session, target)

    val usm = session.getUSM
    val usmUser = new UsmUser(userName, authProtocolOid, authPassphrase, privacyProtocolOid, privacyPassphrase)
    if (engineId == null)
      usm.addUser(usmUser)
    else {
      usm.addUser(userName, new OctetString(engineId), usmUser)
      target.setAuthoritativeEngineID(engineId)
    }

    target
  }

  def createTarget(session: Snmp, addr: InetAddress, options: GetOptions): snmpTarget = {
    options.version match {
      case SnmpVersion.v1 ⇒ createCommunityTarget(addr, options)

      case SnmpVersion.v2c ⇒
        val target = createCommunityTarget(addr, options)
        target.setVersion(SnmpConstants.version2c)
        target

      case SnmpVersion.v3 ⇒ createUserTarget(session, addr, options)
    }
  }
}
