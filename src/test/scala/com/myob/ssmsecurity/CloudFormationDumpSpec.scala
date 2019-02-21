package com.myob.ssmsecurity

import com.myob.ssmsecurity.interpreters.StateInterpreters._
import com.myob.ssmsecurity.models.{StoredUser, UserName}
import kafka.security.auth._
import org.apache.kafka.common.resource.PatternType
import org.apache.kafka.common.security.auth.KafkaPrincipal
import org.apache.kafka.common.security.scram.internals.{ScramFormatter, ScramMechanism}
import org.scalatest.{FlatSpec, Matchers}

class CloudFormationDumpSpec extends FlatSpec with Matchers {

  private def storedUser(userName: String): StoredUser =
    StoredUser(UserName(userName),
      new ScramFormatter(ScramMechanism.SCRAM_SHA_256).generateCredential("password", 1024))

  behavior of "sync"

  it should "return ssm config for topics" in {
    val kafkaTopics = new KafkaTopicsAlgState(Set("a-topic"))

    val dump = new CloudFormationDump("example", kafkaTopics, new KafkaUsersAlgState(Set()), new KafkaAclsAlgState(Map()))

    val (_, cf) = dump.dump.run(SystemState()).value
    val expected = """  a-topic:
                     |    Type: AWS::SSM::Parameter
                     |    Properties:
                     |      Type: String
                     |      Name: /kafka-security/example/topics/a-topic
                     |      Value: 1,6,24
                     |""".stripMargin

    cf shouldBe expected
  }

  it should "return ssm config for acls" in {

    val dump = new CloudFormationDump("example",
      new KafkaTopicsAlgState(Set()),
      new KafkaUsersAlgState(Set(storedUser("user-a"))),
      new KafkaAclsAlgState(Map(
        Resource(Topic, "test-topic-1", PatternType.LITERAL) -> Set(Acl(new KafkaPrincipal("User", "user-a"), Allow, "*", Write)),
        Resource(Group, "test-consumer-group", PatternType.LITERAL) -> Set(Acl(new KafkaPrincipal("User", "user-a"), Deny, "*", Read)),
        Resource(Topic, "test-topic-2", PatternType.LITERAL) -> Set(Acl(new KafkaPrincipal("User", "user-b"), Allow, "*", Read))
      )))

    val (_, cf) = dump.dump.run(SystemState()).value

    val expected = """  user-aAcl:
                  #    Type: AWS::SSM::Parameter
                  #    Properties:
                  #      Type: String
                  #      Name: /kafka-security/example/users/user-a
                  #      Value: |
                  #        Topic,LITERAL,test-topic-1,Write,Allow,*
                  #        Group,LITERAL,test-consumer-group,Read,Deny,*
                  #      """.stripMargin('#')

    cf.trim shouldBe expected.trim
  }

}
