package com.myob.ssmsecurity

import cats.Monad
import cats.implicits._
import com.myob.ssmsecurity.algebras.{KafkaTopicsAlg, LogAlg, MetricsAlg, SsmAlg}
import com.myob.ssmsecurity.models.{Topic, TopicsCreated, TopicsFailed, TopicsOutOfSync}

class TopicSync[F[_]: Monad](clusterName: String,
                             kafkaTopics: KafkaTopicsAlg[F],
                             ssm: SsmAlg[F],
                             metrics: MetricsAlg[F],
                             log: LogAlg[F]) {

  private def ssmConfig: SsmConfig[F] = new SsmConfig[F](clusterName, ssm)

  private def formatTopic(topic: Topic): String = {
    val retention = topic.retentionHs.map(_.value.toString).getOrElse("-")
    s"${topic.replicationFactor.value},${topic.partitionCount.value},$retention"
  }

  private def outOfSync(existingTopics: Set[Topic], ssmTopics: Set[Topic]): Set[String] = {
    val overlappingSsm = ssmTopics.filter(topic => existingTopics.map(_.name).contains(topic.name))
    val overlappingExisting = existingTopics.filter(topic => ssmTopics.map(_.name).contains(topic.name))
    val outOfSync = overlappingSsm -- overlappingExisting

    outOfSync.flatMap(ssmTopic => existingTopics.find(_.name == ssmTopic.name)
      .map(existingTopic =>
        s"Topic '${ssmTopic.name}' is out of sync, config from SSM: ${formatTopic(ssmTopic)}, config from Kafka: ${formatTopic(existingTopic)}")
    )
  }

  val sync: F[Unit] = for {
    ssmResult <- ssmConfig.getTopics
    (ssmErrors, ssmTopics) = ssmResult

    _ <- ssmErrors.map(_.value).traverse(log.error)
    _ <- metrics.sendMetric(TopicsFailed(ssmErrors.length))
    existingTopics <- kafkaTopics.getTopics
    topicsToCreate = ssmTopics.filterNot(topic => existingTopics.map(_.name).contains(topic.name))
    _ <- if (topicsToCreate.nonEmpty) log.info(s"Creating ${topicsToCreate.size} topics") else
      log.info("No topics to create")
    _ <- topicsToCreate.toList.traverse(topic => for {
      _ <- log.info(s"Creating topic $topic")
      _ <- kafkaTopics.createTopic(topic)
    } yield ())
    outOfSyncErrors = outOfSync(existingTopics, ssmTopics)
    _ <- outOfSyncErrors.toList.traverse(log.error)
    _ <- metrics.sendMetric(TopicsOutOfSync(outOfSyncErrors.size))
    _ <- metrics.sendMetric(TopicsCreated(topicsToCreate.size))
  } yield ()

}
