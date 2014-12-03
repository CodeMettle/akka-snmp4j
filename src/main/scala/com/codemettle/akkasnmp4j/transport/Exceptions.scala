/*
 * Exceptions.scala
 *
 * Updated: Dec 3, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle.akkasnmp4j.transport

import java.net.InetSocketAddress

import scala.util.control.NoStackTrace

/**
 * @author steven
 *
 */
case class BindFailedException(bindAddr: InetSocketAddress)
    extends Exception(s"Binding failed to $bindAddr") with NoStackTrace

case class TransportShuttingDownException() extends Exception("Transport is stopping") with NoStackTrace

case class TransportRequestTimeout(method: String) extends Exception(s"Timed out in $method") with NoStackTrace
