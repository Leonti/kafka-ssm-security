package com.myob.ssmsecurity.interpreters

import cats.effect.IO
import com.myob.ssmsecurity.algebras.KafkaUsersAlg
import com.myob.ssmsecurity.models.{StoredUser, User, UserName}
import kafka.admin.AdminProxy
import kafka.zk.AdminZkClient
import org.apache.kafka.common.security.scram.internals.ScramCredentialUtils

class KafkaUsers(adminZkClient: AdminZkClient) extends KafkaUsersAlg[IO] {

  override def createUser(user: User): IO[Unit] = {
    // Using the custom AdminProxy shim to add a user.
    AdminProxy.addUser(user.name, user.password, adminZkClient)
  }

  override def updateUser(user: User):IO[Unit] = createUser(user)

  override def getStoredUsers: IO[Set[StoredUser]] = IO {
    adminZkClient.fetchAllEntityConfigs("users")
      .filter({ case (_, props) => props.containsKey("SCRAM-SHA-256") })
      .map({ case (name, props) => StoredUser(UserName(name), ScramCredentialUtils.credentialFromString(props.getProperty("SCRAM-SHA-256"))) })
      .toSet
  }

  override def removeUser(userName: UserName): IO[Unit] = {
    AdminProxy.removeUser(userName, adminZkClient)
  }
}
