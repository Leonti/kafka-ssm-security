package com.myob.ssmsecurity

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import com.myob.ssmsecurity.algebras.SsmAlg
import com.myob.ssmsecurity.models._
import kafka.security.auth.{Topic => _, _}
import org.apache.kafka.common.resource.PatternType
import org.apache.kafka.common.security.auth.KafkaPrincipal

import scala.util.Try

class SsmConfig[F[_]: Monad](clusterName: String, ssmAlg: SsmAlg[F]) {

  private def parameterToTopic(p: SsmParameter): Either[Error, Topic] = Try {

    val retentionHs = p.value.split(",")(2) match {
      case "-" => None
      case hours => Some(RetentionHs(hours.toLong))
    }

    Topic(p.name.split("/").last,
      ReplicationFactor(p.value.split(",")(0).toInt),
      PartitionCount(p.value.split(",")(1).toInt),
      retentionHs
    )
  }.toEither.leftMap(t => Error(s"Failed to parse topic `${p.name}` with value `${p.value}` ${t.getMessage}"))

  private def lineToAcl(parameterName: String)(l: Array[String]): Either[Error, (Resource, Acl)] = Try {
    val resourceType = ResourceType.fromString(l(0))
    val resourceName = l(2)
    val patternType = PatternType.fromString(l(1).toUpperCase)
    val resource = Resource(resourceType, resourceName, patternType)

    val kafkaPrincipal = new KafkaPrincipal("User", parameterName.split("/").last)

    val permissionType = PermissionType.fromString(l(4))
    val host = l(5)
    val operation = Operation.fromString(l(3))

    val acl = Acl(kafkaPrincipal, permissionType, host, operation)

    (resource, acl)
  }.toEither.leftMap(t => Error(s"Failed to parse acl line `${l.mkString(",")}`, for $parameterName: ${t.getMessage}"))

  private def parameterToAcls(p: SsmParameter): Either[Error, List[(Resource, Acl)]] =
    Try(p.value.split("\n").map(_.trim).map(_.split(",")))
      .toEither
      .leftMap(t => Error(s"Failed to split acl lines `${p.value}`, for `${p.name}`: ${t.getMessage}"))
      .flatMap(a => a.toList.traverse(lineToAcl(p.name)))

  val getTopics: F[(List[Error], Set[Topic])] = for {
    names <- ssmAlg.getParameterNames(s"/kafka-security/$clusterName/topics")
    parameters <- names.toList.traverse(ssmAlg.getParameter)
  } yield {
    val (errors, topics) = parameters.flatten.map(parameterToTopic).separate
    (errors, topics.toSet)
  }

  val getAcls: F[(List[Error], Set[(Resource, Acl)])] = for {
    names <- ssmAlg.getParameterNames(s"/kafka-security/$clusterName/users")
    parameters <- names.toList.traverse(ssmAlg.getParameter)
  } yield {
    val (errors, acls) = parameters.flatten.map(parameterToAcls).separate
    (errors, acls.flatten.toSet)
  }

  private def parseUserName(parameterName: String): Either[Error, String] =
    Try(parameterName.split("/").last).toEither
      .leftMap(t => Error(s"Failed to parse username from parameter`$parameterName` ${t.getMessage}"))

  private def toUser(parameterName: String): F[Either[Error, User]] = (for {
    userName <- EitherT.fromEither[F](parseUserName(parameterName))
    passwordParameterOption <- EitherT.liftF(ssmAlg.getParameter(s"/kafka-security/$clusterName/user-passwords/$userName"))
    passwordParameter <- EitherT.fromEither[F](passwordParameterOption match {
      case Some(p) => Right(p)
      case None => Left(Error(s"Could not find password for a user $userName"))
    })
  } yield User(UserName(userName), Password(passwordParameter.value))).value

  val getUsers: F[(List[Error], Set[User])] = for {
    parameterNames <- ssmAlg.getParameterNames(s"/kafka-security/$clusterName/users")
    users <- parameterNames.toList.traverse(toUser)
  } yield {
    val (errors, us) = users.separate
    (errors, us.toSet)
  }

}
