/*
 * CommunityTarget.scala
 *
 * Updated: Dec 3, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle.akkasnmp4j.util

import java.net.InetAddress

import org.snmp4j.mp.SnmpConstants
import org.snmp4j.security.SecurityModel
import org.snmp4j.smi.{Address, OctetString, UdpAddress}

import com.codemettle.akkasnmp4j.util.CommunityTarget.SNMPVersion

/**
 * @author steven
 *
 */
case class CommunityTarget(addr: Address, community: String = "public", version: SNMPVersion.Value = SNMPVersion.v1) {
    def toSnmp4j = {
        val ret = new org.snmp4j.CommunityTarget(addr, new OctetString(community))
        version match {
            case SNMPVersion.v2c ⇒ ret.setSecurityModel(SecurityModel.SECURITY_MODEL_SNMPv2c)
            case SNMPVersion.v3 ⇒ ret.setVersion(SnmpConstants.version3)
            case _ ⇒
        }
        ret
    }
}

object CommunityTarget {
    object SNMPVersion extends Enumeration {
        type SNMPVersion = Value
        val v1, v2c, v3 = Value
    }

    def udp(addr: InetAddress, port: Int = 161, community: String = "public", version: SNMPVersion.Value = SNMPVersion.v1) = {
        apply(new UdpAddress(addr, port), community, version)
    }
}
