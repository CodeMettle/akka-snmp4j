/*
 * package.scala
 *
 * Updated: Dec 3, 2014
 *
 * Copyright (c) 2014, CodeMettle
 */
package com.codemettle

import akka.actor.{ActorContext, ActorRefFactory, ExtendedActorSystem}

/**
 * @author steven
 *
 */
package object akkasnmp4j {

    // stolen from spray.util
    def actorSystem(implicit refFactory: ActorRefFactory): ExtendedActorSystem = refFactory match {
        case x: ActorContext ⇒ actorSystem(x.system)
        case x: ExtendedActorSystem ⇒ x
        case x ⇒ throw new IllegalArgumentException(s"Unsupported ActorRefFactory implementation: $refFactory")
    }
}
