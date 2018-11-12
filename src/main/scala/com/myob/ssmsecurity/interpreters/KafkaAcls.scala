package com.myob.ssmsecurity.interpreters

import cats.effect.IO
import com.myob.ssmsecurity.algebras.KafkaAclsAlg
import kafka.security.auth.{Acl, Resource, SimpleAclAuthorizer}

import scala.collection.JavaConverters._

class KafkaAcls(zookeeperConnect: String) extends KafkaAclsAlg[IO] {

  private def authorizer() = {
    val simpleAclAuthorizer = new SimpleAclAuthorizer()
    val configs = Map(
      "zookeeper.connect" -> zookeeperConnect
    )
    simpleAclAuthorizer.configure(configs.asJava)
    simpleAclAuthorizer
  }

  override def getKafkaAcls: IO[Map[Resource, Set[Acl]]] = IO(authorizer())
    .bracket { auth => IO(auth.getAcls())} { auth => IO(auth.close()) }

  override def addAcls(toAdd: Map[Resource, Set[Acl]]): IO[Unit] = IO(authorizer())
    .bracket { auth => IO {
      toAdd.foreach {
        case (resource, acls) => auth.addAcls(acls, resource)
      }
    }} { auth => IO(auth.close()) }

  override def removeAcls(toRemove: Map[Resource, Set[Acl]]): IO[Unit] = IO(authorizer())
    .bracket { auth => IO {
      toRemove.foreach {
        case (resource, acls) => auth.removeAcls(acls, resource)
      }
    }} { auth => IO(auth.close()) }
}
