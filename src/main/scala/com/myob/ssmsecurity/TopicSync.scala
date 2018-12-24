package com.myob.ssmsecurity

import cats.Monad
import cats.implicits._
import com.myob.ssmsecurity.algebras.{KafkaTopicsAlg, LogAlg, MetricsAlg, SsmAlg}
import com.myob.ssmsecurity.models.{TopicsCreated, TopicsFailed}

class TopicSync[F[_]: Monad](clusterName: String,
                             kafkaTopics: KafkaTopicsAlg[F],
                             ssm: SsmAlg[F],
                             metrics: MetricsAlg[F],
                             log: LogAlg[F]) {

  private def ssmConfig: SsmConfig[F] = new SsmConfig[F](clusterName, ssm)

  val sync: F[Unit] = for {
    ssmResult <- ssmConfig.getTopics
    (ssmErrors, ssmTopics) = ssmResult

    _ <- ssmErrors.map(_.value).traverse(log.error)
    _ <- metrics.sendMetric(TopicsFailed(ssmErrors.length))
    existingTopics <- kafkaTopics.getTopicNames
    topicsToCreate = ssmTopics.filterNot(topic => existingTopics.contains(topic.name))
    _ <- if (topicsToCreate.nonEmpty) log.info(s"Creating ${topicsToCreate.size} topics") else
      log.info("No topics to create")
    _ <- topicsToCreate.toList.traverse(topic => for {
      _ <- log.info(s"Creating topic $topic")
      _ <- kafkaTopics.createTopic(topic)
    } yield ())
    _ <- metrics.sendMetric(TopicsCreated(topicsToCreate.size))
  } yield ()

}
