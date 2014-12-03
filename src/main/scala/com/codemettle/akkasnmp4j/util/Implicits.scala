/*
 * Implicits.scala
 *
 * Updated: Dec 3, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle.akkasnmp4j.util

import java.net.InetSocketAddress

import org.snmp4j.smi.UdpAddress

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

    implicit def inetSocketAddressToUdpAddress(addr: InetSocketAddress): UdpAddress = addr.toUdpAddress
    implicit def udpAddressToInetSocketAddress(addr: UdpAddress): InetSocketAddress = addr.toInetSocketAddress
}
