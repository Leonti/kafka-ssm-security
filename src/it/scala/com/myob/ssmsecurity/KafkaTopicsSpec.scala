package com.myob.ssmsecurity

import java.util.UUID.randomUUID

import com.myob.ssmsecurity.interpreters.KafkaTopics
import com.myob.ssmsecurity.models.{PartitionCount, ReplicationFactor, RetentionHs, Topic}
import kafka.zk.{AdminZkClient, KafkaZkClient}
import org.apache.kafka.common.security.JaasUtils
import org.apache.kafka.common.utils.Time
import org.scalatest.{FlatSpec, Matchers}

class KafkaTopicsSpec extends FlatSpec with Matchers {

  private val zkEndpoint = sys.env.getOrElse("ZK_ENDPOINT", "localhost:2181")

  behavior of "KafkaTopics"

  it should "create a topic and read topic names" in {

    val adminZkClient = new AdminZkClient(KafkaZkClient(zkEndpoint, JaasUtils.isZkSecurityEnabled, 30000, 30000,
      Int.MaxValue, Time.SYSTEM))

    val kafkaTopics = new KafkaTopics(adminZkClient)

    val topicName = s"topic-${randomUUID().toString}"
    kafkaTopics.createTopic(Topic(topicName, ReplicationFactor(1), PartitionCount(1), RetentionHs(1))).unsafeRunSync()

    val topicNames = kafkaTopics.getTopicNames.unsafeRunSync()

    topicNames should contain (topicName)
  }
}
