package com.codemettle.akkasnmp4j.util

import java.net.{InetAddress, InetSocketAddress}

import org.snmp4j.mp.{MPv3, MessageProcessingModel, SnmpConstants}
import org.snmp4j.security._
import org.snmp4j.security.nonstandard.PrivAES256With3DESKeyExtension
import org.snmp4j.smi.{OctetString, UdpAddress}
import org.snmp4j.{CommunityTarget, Snmp, UserTarget, Target => snmpTarget}

import com.codemettle.akkasnmp4j.config.{CredOptions, GetOptions}

object Target {

  private def createCommunityTarget(addr: InetSocketAddress, options: GetOptions, credOptions: CredOptions,
                                    forWrite: Boolean = false): CommunityTarget = {
    val comm = new OctetString(if (forWrite) credOptions.writeCommunity else credOptions.readCommunity)
    val target = new CommunityTarget(new UdpAddress(addr.getAddress, addr.getPort), comm)
    target setRetries options.retries
    options.timeout foreach (t => target setTimeout t.toMillis)
    target
  }

  private def createUserTarget(session: Snmp, addr: InetSocketAddress, options: GetOptions, credOpts: CredOptions): UserTarget = {

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

    val userName = new OctetString(credOpts.user)
    target.setAddress(new UdpAddress(addr.getAddress, addr.getPort))
    target.setVersion(SnmpConstants.version3)
    target.setSecurityLevel(credOpts.securityLevel.id)
    target.setSecurityName(userName)
    val authPassphrase = new OctetString(credOpts.authPassPhrase)
    val privacyPassphrase = new OctetString(credOpts.privacyPassPhrase)
    val authProtocolOid = credOpts.authProtocol.map({
      case SnmpAuthProtocol.SHA => AuthSHA.ID
      case SnmpAuthProtocol.MD5 => AuthMD5.ID
    }).orNull
    val privacyProtocolOid = credOpts.privacyProtocol.map({
      case SnmpPrivacyProtocol.DES => PrivDES.ID
      case SnmpPrivacyProtocol.AES => PrivAES128.ID
      case SnmpPrivacyProtocol.AES192 => PrivAES192.ID
      case SnmpPrivacyProtocol.AES256 => PrivAES256.ID
      case SnmpPrivacyProtocol.CISCO_AES256 => PrivAES256With3DESKeyExtension.ID
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

  def createTarget(session: Snmp, addr: InetSocketAddress, options: GetOptions, credOptions: CredOptions,
                   forWrite: Boolean = false): snmpTarget = {
    credOptions.version match {
      case SnmpVersion.v1 => createCommunityTarget(addr, options, credOptions, forWrite)

      case SnmpVersion.v2c =>
        val target = createCommunityTarget(addr, options, credOptions, forWrite)
        target.setVersion(SnmpConstants.version2c)
        target

      case SnmpVersion.v3 => createUserTarget(session, addr, options, credOptions)
    }
  }

  @deprecated("Use method that takes InetSocketAddress", "0.12.0")
  def createTarget(session: Snmp, ipAddr: InetAddress, options: GetOptions, credOptions: CredOptions,
                   forWrite: Boolean): snmpTarget =
    createTarget(session, new InetSocketAddress(ipAddr, options.port), options, credOptions, forWrite)
}
