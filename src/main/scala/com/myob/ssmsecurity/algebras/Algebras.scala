package com.myob.ssmsecurity.algebras

import com.myob.ssmsecurity.models._
import kafka.security.auth.{Acl, Resource}

trait SsmAlg[F[_]] {
  def getParameterNames(filter: String): F[Set[String]]
  def getParameter(name: String): F[Option[SsmParameter]]
}

trait KafkaAclsAlg[F[_]] {
  def getKafkaAcls: F[Map[Resource, Set[Acl]]]
  def addAcls(acls: Map[Resource, Set[Acl]]): F[Unit]
  def removeAcls(acls: Map[Resource, Set[Acl]]): F[Unit]
}

trait KafkaTopicsAlg[F[_]] {
  def createTopic(topic: Topic): F[Unit]
  def getTopicNames: F[Set[String]]
}

trait KafkaUsersAlg[F[_]] {
  def createUser(user: User): F[Unit]
  def updateUser(user: User): F[Unit]
  def getStoredUsers: F[Set[StoredUser]]
  def removeUser(userName: UserName): F[Unit]
}

trait LogAlg[F[_]] {
  def info(message: String): F[Unit]
  def error(message: String): F[Unit]
}

trait MetricsAlg[F[_]] {
  def sendMetric(metric: Metric): F[Unit]
}
