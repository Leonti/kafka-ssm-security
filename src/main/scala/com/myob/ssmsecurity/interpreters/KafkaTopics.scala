package com.myob.ssmsecurity.interpreters

import java.util.Properties

import cats.effect.IO
import com.myob.ssmsecurity.algebras.KafkaTopicsAlg
import com.myob.ssmsecurity.models.{PartitionCount, ReplicationFactor, RetentionHs, Topic}
import kafka.zk.{AdminZkClient, KafkaZkClient}
import scala.concurrent.duration._

class KafkaTopics(zkClient: KafkaZkClient) extends KafkaTopicsAlg[IO] {
  val adminZkClient = new AdminZkClient(zkClient)

  override def createTopic(topic: Topic): IO[Unit] = IO {
    val topicProperties = new Properties
    topic.retentionHs.foreach(retentionHs =>
      topicProperties.setProperty("retention.ms", retentionHs.value.hours.toMillis.toString)
    )

    adminZkClient.createTopic(topic.name, topic.partitionCount.value, topic.replicationFactor.value, topicProperties)
  }

  override def getTopics: IO[Set[Topic]] = IO {
    val configs = adminZkClient.getAllTopicConfigs()
    val assignments = zkClient.getPartitionAssignmentForTopics(configs.keys.toSet)

    configs.toList
      .flatMap({ case (topic, config) => assignments.get(topic).map(a => {
        val retentionHs = Option(config.getProperty("retention.ms")).map(_.toLong.millis.toHours).map(RetentionHs.apply)
        Topic(topic, ReplicationFactor(a.head._2.size), PartitionCount(a.size), retentionHs)
      })
    }).toSet
  }
}
