package com.myob.ssmsecurity

import cats.Monad
import cats.implicits._
import com.myob.ssmsecurity.algebras.{KafkaAclsAlg, KafkaTopicsAlg, KafkaUsersAlg}
import com.myob.ssmsecurity.models.{Topic, UserName}
import kafka.security.auth.{Acl, Resource}

class CloudFormationDump[F[_]: Monad](
                                       clusterName: String,
                                       kafkaTopics: KafkaTopicsAlg[F],
                                       kafkaUsers: KafkaUsersAlg[F],
                                       kafkaAcls: KafkaAclsAlg[F]
                                     ) {

  private def flattenKafkaAcls(kafkaGroupedAcls: Map[Resource, Set[Acl]]): Set[(Resource, Acl)] = {
    kafkaGroupedAcls.keySet.flatMap(resource =>
      kafkaGroupedAcls(resource).map((resource, _)))
  }

  def dump: F[String] = for {
    topics <- kafkaTopics.getTopics.map(_.map(topicToCf).mkString("\n"))
    userNames <- kafkaUsers.getStoredUsers.map(_.map(_.name))
    aclMap <- kafkaAcls.getKafkaAcls
    flattenedAcls = flattenKafkaAcls(aclMap)
    userAcls = userNames.map(aclsToCf(flattenedAcls)).mkString("\n")
  } yield List(topics, userAcls).mkString("\n")

  private def aclsToCf(acls: Set[(Resource, Acl)])(userName: UserName): String = {

    val aclsValue = acls.filter({ case (_, acl) => acl.principal.getName == userName.value })
      .map({ case (resource, acl) =>
      s"${resource.resourceType.name},${resource.patternType.name},${resource.name},${acl.operation.name},${acl.permissionType.name},${acl.host}" })
      .mkString("\n        ")

    s"""  ${userName.value}Acl:
       #    Type: AWS::SSM::Parameter
       #    Properties:
       #      Type: String
       #      Name: /kafka-security/$clusterName/users/${userName.value}
       #      Value: |
       #        $aclsValue""".stripMargin('#')

  }


  private def topicToCf(topic: Topic): String = {
    val retention = topic.retentionHs.map(_.value.toString).getOrElse("-")

    s"""  ${topic.name}:
       |    Type: AWS::SSM::Parameter
       |    Properties:
       |      Type: String
       |      Name: /kafka-security/$clusterName/topics/${topic.name}
       |      Value: ${topic.replicationFactor.value},${topic.partitionCount.value},$retention""".stripMargin
  }


}
