package com.myob.ssmsecurity

import com.myob.ssmsecurity.interpreters.SsmAlgTest
import com.myob.ssmsecurity.interpreters.StateInterpreters._
import com.myob.ssmsecurity.models._
import org.scalatest.{FlatSpec, Matchers}

class TopicSyncSpec extends FlatSpec with Matchers {

  behavior of "sync"

  it should "create a topic if doesn't exist" in {

    val kafkaTopics = new KafkaTopicsAlgState(Set("a-topic"))
    val ssm = new SsmAlgTest[TestProgram](
      SsmParameter("/kafka-security/ci-cluster/topics/test-topic", "1,10,24")
    )

    val topicSync = new TopicSync("ci-cluster", kafkaTopics, ssm, new MetricsAlgState(), new LogAlgState())

    val (state, _) = topicSync.sync.run(SystemState()).value

    state.topics shouldBe List(Topic("test-topic", ReplicationFactor(1), PartitionCount(10), RetentionHs(24)))
    state.metricsSent.toSet shouldBe Set(TopicsCreated(1), TopicsFailed(0))
  }

  it should "not create a topic if already exists" in {

    val kafkaTopics = new KafkaTopicsAlgState(Set("a-topic", "test-topic"))
    val ssm = new SsmAlgTest[TestProgram](
      SsmParameter("/kafka-security/ci-cluster/topics/test-topic", "1,10,24")
    )

    val topicSync = new TopicSync("ci-cluster", kafkaTopics, ssm, new MetricsAlgState(), new LogAlgState())

    val (state, _) = topicSync.sync.run(SystemState()).value

    state.topics shouldBe List()
    state.metricsSent.toSet shouldBe Set(TopicsCreated(0), TopicsFailed(0))
  }

  "sync" should "log errors for failed topics and continue with the parsed ones" in {

    val kafkaTopics = new KafkaTopicsAlgState(Set("a-topic"))
    val ssm = new SsmAlgTest[TestProgram](
      SsmParameter("/kafka-security/ci-cluster/topics/unparseable-topic", "unparseable"),
      SsmParameter("/kafka-security/ci-cluster/topics/test-topic", "1,10,24")
    )

    val topicSync = new TopicSync("ci-cluster", kafkaTopics, ssm, new MetricsAlgState(), new LogAlgState())

    val (state, _) = topicSync.sync.run(SystemState()).value

    state.topics shouldBe List(Topic("test-topic", ReplicationFactor(1), PartitionCount(10), RetentionHs(24)))
    state.errors.length shouldBe 1
    state.metricsSent.toSet shouldBe Set(TopicsCreated(1), TopicsFailed(1))
  }
}
