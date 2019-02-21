package com.myob.ssmsecurity

import java.util.UUID.randomUUID

import com.myob.ssmsecurity.interpreters.KafkaUsers
import com.myob.ssmsecurity.models._
import kafka.zk.{AdminZkClient, KafkaZkClient}
import org.apache.kafka.common.security.JaasUtils
import org.apache.kafka.common.utils.Time
import org.scalatest.{FlatSpec, Matchers}

class KafkaUsersSpec extends FlatSpec with Matchers {

  private val zkEndpoint = sys.env.getOrElse("ZK_ENDPOINT", "localhost:2181")

  behavior of "KafkaUsers"

  it should "create users, read usernames, and delete users" in {

    val adminZkClient = new AdminZkClient(KafkaZkClient(zkEndpoint, JaasUtils.isZkSecurityEnabled, 30000, 30000,
      Int.MaxValue, Time.SYSTEM))

    val kafkaUsers = new KafkaUsers(adminZkClient)
    val userName = UserName(s"user-${randomUUID().toString}")

    kafkaUsers.createUser(User(userName, Password("password"))).unsafeRunSync()

    val storedUsersAfterCreation = kafkaUsers.getStoredUsers.unsafeRunSync()
    val usernamesAfterCreation = storedUsersAfterCreation.map(_.name)
    usernamesAfterCreation should contain(userName)

    kafkaUsers.removeUser(userName).unsafeRunSync()

    val storedUsersAfterRemoval = kafkaUsers.getStoredUsers.unsafeRunSync()
    val usernamesAfterRemoval = storedUsersAfterRemoval.map(_.name)
    usernamesAfterRemoval should not contain userName
  }
}
