package com.myob.ssmsecurity

import com.myob.ssmsecurity.interpreters.KafkaAcls
import kafka.security.auth._
import org.apache.kafka.common.resource.PatternType
import org.apache.kafka.common.security.auth.KafkaPrincipal
import org.scalatest.{FlatSpec, Matchers}

class KafkaAclsSpec extends FlatSpec with Matchers {

  private val zkEndpoint = sys.env.getOrElse("ZK_ENDPOINT", "localhost:2181")

  behavior of "KafkaAcls"

  it should "create, read and delete ACLs" in {

    val kafkaAcls = new KafkaAcls(zkEndpoint)

    val resource = Resource(Group, "test-group", PatternType.LITERAL)
    val acl = Acl(new KafkaPrincipal("User", "test-user"), Allow, "*", All)

    val writtenAcls = Map(resource -> Set(acl))

    kafkaAcls.addAcls(writtenAcls).unsafeRunSync()
    val readAcls = kafkaAcls.getKafkaAcls.unsafeRunSync()

    readAcls shouldBe writtenAcls

    kafkaAcls.removeAcls(readAcls).unsafeRunSync()
    val readAfterRemoval = kafkaAcls.getKafkaAcls.unsafeRunSync()

    readAfterRemoval shouldBe Map()
  }
}
