package com.myob.ssmsecurity

import com.myob.ssmsecurity.interpreters.SsmAlgTest
import com.myob.ssmsecurity.interpreters.StateInterpreters._
import com.myob.ssmsecurity.models.{SsmParameter, _}
import org.apache.kafka.common.security.scram.internals.{ScramFormatter, ScramMechanism}
import org.scalatest.{FlatSpec, Matchers}

class UserSyncSpec extends FlatSpec with Matchers {

  def storedUser(userName: UserName, password: String): StoredUser =
    StoredUser(userName,
      new ScramFormatter(ScramMechanism.SCRAM_SHA_256).generateCredential(password, 1024))

  behavior of "sync"

  it should "create a user if doesn't exist" in {

    val kafkaUsers = new KafkaUsersAlgState(Set())
    val ssm = new SsmAlgTest[TestProgram](
      SsmParameter("/kafka-security/ci-cluster/users/test-user", ""),
      SsmParameter("/kafka-security/ci-cluster/user-passwords/test-user", "password")
    )

    val userSync = new UserSync("ci-cluster", kafkaUsers, ssm, new LogAlgState())

    val (state, _) = userSync.sync.run(SystemState()).value

    state.usersAdded shouldBe List(User(UserName("test-user"), Password("password")))
  }

  it should "delete a user if it doesn't exist" in {
    val userName = UserName("shouldBeRemoved")
    val kafkaUsers = new KafkaUsersAlgState(Set(storedUser(userName, "password")))
    val ssm = new SsmAlgTest[TestProgram]()

    val userSync = new UserSync("ci-cluster", kafkaUsers, ssm, new LogAlgState())

    val (state, _) = userSync.sync.run(SystemState()).value

    state.userNamesRemoved shouldBe List(userName)
  }

  it should "update user password if outdated" in {
    val userName = UserName("shouldBeUpdated")

    val kafkaUsers = new KafkaUsersAlgState(Set(storedUser(userName, "old-password")))
    val ssm = new SsmAlgTest[TestProgram](
      SsmParameter("/kafka-security/ci-cluster/user-passwords/shouldBeUpdated", "password")
    )

    val userSync = new UserSync("ci-cluster", kafkaUsers, ssm, new LogAlgState())

    val (state, _) = userSync.sync.run(SystemState()).value

    state.usersUpdated shouldBe List(User(UserName("shouldBeUpdated"), Password("password")))
  }

  it should "not update or create user if password is correct" in {
    val userName = UserName("shouldBeUpdated")

    val kafkaUsers = new KafkaUsersAlgState(Set(storedUser(userName, "password")))
    val ssm = new SsmAlgTest[TestProgram](
      SsmParameter("/kafka-security/ci-cluster/user-passwords/shouldBeUpdated", "password")
    )

    val userSync = new UserSync("ci-cluster", kafkaUsers, ssm, new LogAlgState())

    val (state, _) = userSync.sync.run(SystemState()).value

    state.usersUpdated shouldBe List()
    state.usersAdded shouldBe List()
  }

  "sync" should "log errors for failed users and continue with the parsed ones" in {

    val kafkaUsers = new KafkaUsersAlgState(Set())
    val ssm = new SsmAlgTest[TestProgram](
      SsmParameter("/kafka-security/ci-cluster/users/test-user", ""),
      SsmParameter("/kafka-security/ci-cluster/user-passwords/test-user", "password"),
      SsmParameter("/kafka-security/ci-cluster/users/test-user-no-password", "")
    )

    val userSync = new UserSync("ci-cluster", kafkaUsers, ssm, new LogAlgState())

    val (state, _) = userSync.sync.run(SystemState()).value

    state.usersAdded shouldBe List(User(UserName("test-user"), Password("password")))
    state.errors.length shouldBe 1
  }
}
