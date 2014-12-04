/*
 * CommunityTarget.scala
 *
 * Updated: Dec 3, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle.akkasnmp4j.util

import java.net.InetAddress

import org.snmp4j.smi.{OctetString, Address, UdpAddress}

/**
 * @author steven
 *
 */
case class CommunityTarget(addr: Address, community: String = "public") {
    def toSnmp4j = new org.snmp4j.CommunityTarget(addr, new OctetString(community))
}

object CommunityTarget {
    def udp(addr: InetAddress, port: Int, community: String = "public") = apply(new UdpAddress(addr, port), community)
}
