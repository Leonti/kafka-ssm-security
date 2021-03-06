package com.myob.ssmsecurity

import com.myob.ssmsecurity.interpreters.SsmAlgTest
import com.myob.ssmsecurity.interpreters.StateInterpreters._
import com.myob.ssmsecurity.models.{AclsCreated, AclsFailed, AclsRemoved, SsmParameter}
import kafka.security.auth.{Resource, _}
import org.apache.kafka.common.resource.PatternType
import org.apache.kafka.common.security.auth.KafkaPrincipal
import org.scalatest.{FlatSpec, Matchers}

class AclSyncSpec extends FlatSpec with Matchers {

  behavior of "sync"

  it should "add a single acl" in {
    val ssm = new SsmAlgTest[TestProgram](
      SsmParameter("/kafka-security/ci-cluster/users/test-user",
        "Topic,LITERAL,test-topic,Read,Allow,*\nGroup,LITERAL,test-partition,Write,Allow,*")
    )

    val aclSync = new AclSync("ci-cluster", new KafkaAclsAlgState(Map()), ssm, new MetricsAlgState(), new LogAlgState())

    val (state, _) = aclSync.sync.run(SystemState()).value

    state.aclsAdded.size shouldBe 2
    state.aclsRemoved shouldBe Map()
    state.metricsSent.toSet shouldBe Set(AclsRemoved(0), AclsCreated(2), AclsFailed(0))
  }

  it should "add multiple acls" in {
    val ssm = new SsmAlgTest[TestProgram](
      SsmParameter("/kafka-security/ci-cluster/users/test-user", "Topic,LITERAL,test-topic,Read,Allow,*")
    )

    val aclSync = new AclSync("ci-cluster", new KafkaAclsAlgState(Map()), ssm, new MetricsAlgState(), new LogAlgState())

    val (state, _) = aclSync.sync.run(SystemState()).value

    val expectedAdded = Map(
      Resource(Topic, "test-topic", PatternType.LITERAL) -> Set(Acl(new KafkaPrincipal("User", "test-user"), Allow, "*", Read))
    )

    state.aclsAdded shouldBe expectedAdded
    state.aclsRemoved shouldBe Map()
    state.metricsSent.toSet shouldBe Set(AclsRemoved(0), AclsCreated(1), AclsFailed(0))
  }

  it should "remove acls if they are not in SSM" in {
    val ssm = new SsmAlgTest[TestProgram]()

    val expectedRemoved = Map(
      Resource(Group, "test-group", PatternType.LITERAL) -> Set(Acl(new KafkaPrincipal("User", "test-user"), Allow, "*", Write)),
      Resource(Topic, "test-topic", PatternType.LITERAL) -> Set(Acl(new KafkaPrincipal("User", "test-user"), Allow, "*", Read))
    )
    val aclSync = new AclSync("ci-cluster", new KafkaAclsAlgState(expectedRemoved), ssm, new MetricsAlgState(), new LogAlgState())

    val (state, _) = aclSync.sync.run(SystemState()).value

    state.aclsAdded shouldBe Map()
    state.aclsRemoved shouldBe expectedRemoved
    state.metricsSent.toSet shouldBe Set(AclsRemoved(2), AclsCreated(0), AclsFailed(0))
  }

  it should "add and remove" in {
    val ssm = new SsmAlgTest[TestProgram](
      SsmParameter("/kafka-security/ci-cluster/users/test-user", "Topic,LITERAL,test-topic,Read,Allow,*")
    )

    val expectedAdded = Map(Resource(Topic, "test-topic", PatternType.LITERAL) -> Set(Acl(new KafkaPrincipal("User", "test-user"), Allow, "*", Read)))
    val expectedRemoved = Map(Resource(Group, "test-group", PatternType.LITERAL) -> Set(Acl(new KafkaPrincipal("User", "test-user"), Allow, "*", Write)))

    val aclSync = new AclSync("ci-cluster", new KafkaAclsAlgState(expectedRemoved), ssm, new MetricsAlgState(), new LogAlgState())

    val (state, _) = aclSync.sync.run(SystemState()).value

    state.aclsAdded shouldBe expectedAdded
    state.aclsRemoved shouldBe expectedRemoved
    state.metricsSent.toSet shouldBe Set(AclsRemoved(1), AclsCreated(1), AclsFailed(0))
  }

  it should "do nothing if acls are in sync already" in {
    val ssm = new SsmAlgTest[TestProgram](
      SsmParameter("/kafka-security/ci-cluster/users/test-user", "Topic,LITERAL,test-topic,Read,Allow,*")
    )

    val kafkaAcls = Map(Resource(Topic, "test-topic", PatternType.LITERAL) -> Set(Acl(new KafkaPrincipal("User", "test-user"), Allow, "*", Read)))

    val aclSync = new AclSync("ci-cluster", new KafkaAclsAlgState(kafkaAcls), ssm, new MetricsAlgState(), new LogAlgState())
    val (state, _) = aclSync.sync.run(SystemState()).value

    state.aclsAdded shouldBe Map()
    state.aclsRemoved shouldBe Map()
    state.metricsSent shouldBe List(AclsRemoved(0), AclsCreated(0), AclsFailed(0))
  }

  it should "should show errors for failed parameters and add a single successful ones" in {
    val ssm = new SsmAlgTest[TestProgram](
      SsmParameter("/kafka-security/ci-cluster/users/test-user", "Topic,LITERAL,test-topic,Read,Allow,*"),
      SsmParameter("/kafka-security/ci-cluster/users/unparseable-user", "unparseable")
    )

    val aclSync = new AclSync("ci-cluster", new KafkaAclsAlgState(Map()), ssm, new MetricsAlgState(), new LogAlgState())

    val (state, _) = aclSync.sync.run(SystemState()).value

    val expectedAdded = Map(
      Resource(Topic, "test-topic", PatternType.LITERAL) -> Set(Acl(new KafkaPrincipal("User", "test-user"), Allow, "*", Read))
    )

    state.aclsAdded shouldBe expectedAdded
    state.aclsRemoved shouldBe Map()
    state.errors.length shouldBe 1
    state.metricsSent.toSet shouldBe Set(AclsRemoved(0), AclsCreated(1), AclsFailed(1))
  }

}
