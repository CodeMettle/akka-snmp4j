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
}
