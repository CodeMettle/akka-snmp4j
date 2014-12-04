/*
 * Implicits.scala
 *
 * Updated: Dec 3, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle.akkasnmp4j.util

import java.net.InetSocketAddress

import org.snmp4j.smi.{IpAddress, VariableBinding, OID, UdpAddress}

import scala.language.implicitConversions

/**
 * @author steven
 *
 */
object Implicits {

    implicit class RichInetSocketAddress(val u: InetSocketAddress) extends AnyVal {
        def toUdpAddress: UdpAddress = new UdpAddress(u.getAddress, u.getPort)
    }

    implicit class RichUdpAddress(val u: UdpAddress) extends AnyVal {
        def port = u.getPort
        def inetAddress = u.getInetAddress

        def toInetSocketAddress: InetSocketAddress = new InetSocketAddress(u.getInetAddress, u.getPort)
    }

    implicit class RichString(val u: String) extends AnyVal {
        def toOid = new OID(u)
    }

    implicit class RichVariableBinding(val u: VariableBinding) extends AnyVal {
        def intValue = u.getVariable.toInt
        def longValue = u.getVariable.toLong
        def valueString = u.toValueString
        def ipAddress = u.getVariable match {
            case a: IpAddress ⇒ a
            case _ ⇒ sys.error(s"${u.getVariable.getClass.getSimpleName} is not an IpAddress")
        }
    }

    implicit def inetSocketAddressToUdpAddress(addr: InetSocketAddress): UdpAddress = addr.toUdpAddress
    implicit def udpAddressToInetSocketAddress(addr: UdpAddress): InetSocketAddress = addr.toInetSocketAddress
    implicit def stringToOid(str: String): OID = str.toOid
}
