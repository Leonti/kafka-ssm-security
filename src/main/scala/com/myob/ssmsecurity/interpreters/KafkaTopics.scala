package com.myob.ssmsecurity.interpreters

import java.util.Properties

import cats.effect.IO
import com.myob.ssmsecurity.algebras.KafkaTopicsAlg
import com.myob.ssmsecurity.models.Topic
import kafka.zk.AdminZkClient

import scala.concurrent.duration._

class KafkaTopics(adminZkClient: AdminZkClient) extends KafkaTopicsAlg[IO] {

  override def createTopic(topic: Topic): IO[Unit] = IO {
    val topicProperties = new Properties()
    topicProperties.setProperty("retention.ms", topic.retentionHs.value.hours.toMillis.toString)

    adminZkClient.createTopic(topic.name, topic.partitionCount.value, topic.replicationFactor.value, topicProperties)
  }

  override def getTopicNames: IO[Set[String]] = IO(adminZkClient.getAllTopicConfigs().keys.toSet)
}
