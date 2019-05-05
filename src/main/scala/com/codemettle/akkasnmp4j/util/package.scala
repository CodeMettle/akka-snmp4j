package com.codemettle.akkasnmp4j

import akka.actor.{ActorContext, ActorRefFactory, ActorSystem}

/**
  * Created by steven on 9/20/2017.
  */
package object util {
  import scala.language.implicitConversions

  implicit def actorSystem(implicit arf: ActorRefFactory): ActorSystem = arf match {
    case s: ActorSystem ⇒ s
    case c: ActorContext ⇒ c.system
    case _ ⇒ sys.error("Unsupported ActorRefFactory")
  }

  object SnmpPrivacyProtocol extends Enumeration {
    type SnmpPrivacyProtocol = Value
    val DES, AES, AES192, AES256, CISCO_AES256 = Value
  }

  object SnmpAuthProtocol extends Enumeration {
    type SnmpAuthProtocol = Value
    val MD5, SHA = Value
  }

  object SnmpVersion extends Enumeration {
    type SnmpVersion = Value
    val v1, v2c, v3 = Value
  }

  object SnmpSecurityLevel extends Enumeration {
    type SnmpSecurityLevel = Value
    val noAuthNoPriv: Value = Value(1)
    val authNoPriv: Value = Value(2)
    val authPriv: Value = Value(3)
  }

}
