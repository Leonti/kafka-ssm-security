package com.myob.ssmsecurity

import java.util.Base64

import cats.Monad
import cats.implicits._
import com.myob.ssmsecurity.algebras.{KafkaUsersAlg, LogAlg, MetricsAlg, SsmAlg}
import com.myob.ssmsecurity.models._
import org.apache.kafka.common.security.scram.ScramCredential
import org.apache.kafka.common.security.scram.internals.{ScramFormatter, ScramMechanism}

class UserSync[F[_] : Monad](clusterName: String,
                             kafkaUsers: KafkaUsersAlg[F],
                             ssm: SsmAlg[F],
                             metrics: MetricsAlg[F],
                             log: LogAlg[F]) {

  val sync: F[Unit] = for {
    ssmResult <- ssmConfig.getUsers
    (ssmErrors, ssmUsers) = ssmResult
    _ <- ssmErrors.map(_.value).traverse(log.error)
    _ <- metrics.sendMetric(UsersFailed(ssmErrors.length))
    kafkaStoredUsers <- kafkaUsers.getStoredUsers
    usersToAdd = ssmUsers.filterNot(u => kafkaStoredUsers.map(_.name).contains(u.name))
    userNamesToRemove = kafkaStoredUsers.map(_.name).diff(ssmUsers.map(_.name))
    usersToUpdate = kafkaStoredUsers.filterNot(st => usersToAdd.map(_.name).contains(st.name))
      .filterNot(st => userNamesToRemove.contains(st.name))
      .filterNot(st => passwordIsUpToDate(ssmUsers.find(u => u.name == st.name).get.password, st.credentials))
      .map(st => ssmUsers.find(u => u.name == st.name).get)

    _ <- if (usersToAdd.nonEmpty) log.info("Users to add!") else log.info("No users to add :(")
    _ <- if (userNamesToRemove.nonEmpty) log.info("Users to remove!!!") else log.info("No users to remove")
    _ <- if (usersToUpdate.nonEmpty) log.info("Users to update!") else log.info("No users to update")

    _ <- usersToAdd.toList.traverse(user => for {
      _ <- log.info(s"Adding user: ${user.name}")
      _ <- kafkaUsers.createUser(user)
    } yield ())
    _ <- metrics.sendMetric(UsersCreated(usersToAdd.size))

    _ <- usersToUpdate.toList.traverse(user => for {
      _ <- log.info(s"Updating user: ${user.name}")
      _ <- kafkaUsers.updateUser(user)
    } yield ())
    _ <- metrics.sendMetric(UsersUpdated(usersToUpdate.size))

    _ <- userNamesToRemove.toList.traverse(name => for {
      _ <- log.info(s"Removing user: $name")
      _ <- kafkaUsers.removeUser(name)
    } yield ())
    _ <- metrics.sendMetric(UsersRemoved(userNamesToRemove.size))
  } yield ()

  private def passwordIsUpToDate(password: Password, credential: ScramCredential): Boolean = {
    val formatter = new ScramFormatter(ScramMechanism.SCRAM_SHA_256)
    val pass = formatter.saltedPassword(password.value, credential.salt(), credential.iterations())
    val clientKey = formatter.clientKey(pass)
    val storedKey = formatter.storedKey(clientKey)
    val serverKey = formatter.serverKey(pass)

    Base64.getEncoder.encodeToString(storedKey) == Base64.getEncoder.encodeToString(credential.storedKey()) &&
      Base64.getEncoder.encodeToString(serverKey) == Base64.getEncoder.encodeToString(credential.serverKey())
  }

  private def ssmConfig: SsmConfig[F] = new SsmConfig[F](clusterName, ssm)
}
