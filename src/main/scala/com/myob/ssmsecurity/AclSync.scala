package com.myob.ssmsecurity

import cats.Monad
import cats.implicits._
import com.myob.ssmsecurity.algebras.{KafkaAclsAlg, LogAlg, MetricsAlg, SsmAlg}
import com.myob.ssmsecurity.models.{AclsCreated, AclsFailed, AclsRemoved}
import kafka.security.auth.{Acl, Resource}

class AclSync[F[_]: Monad](clusterName: String,
                           kafkaAcls: KafkaAclsAlg[F],
                           ssm: SsmAlg[F],
                           metrics: MetricsAlg[F],
                           log: LogAlg[F]) {

  private def ssmConfig: SsmConfig[F] = new SsmConfig[F](clusterName, ssm)

  private def flattenKafkaAcls(kafkaGroupedAcls: Map[Resource, Set[Acl]]): Set[(Resource, Acl)] = {
    kafkaGroupedAcls.keySet.flatMap(resource =>
      kafkaGroupedAcls(resource).map((resource, _)))
  }

  private def groupAcls(flattenedAcls: Set[(Resource, Acl)]): Map[Resource, Set[Acl]] = {
    flattenedAcls
      .groupBy { case (r: Resource, _: Acl) => r }
      .mapValues(_.map((y: (Resource, Acl)) => y._2))
  }

  val sync: F[Unit] = for {
    fromKafka <- kafkaAcls.getKafkaAcls.map(flattenKafkaAcls)
    ssmAcls <- ssmConfig.getAcls
    (ssmErrors, fromSsm) = ssmAcls

    _ <- ssmErrors.map(_.value).traverse(log.error)
    _ <- metrics.sendMetric(AclsFailed(ssmErrors.length))
    _ <- if (fromSsm == fromKafka) for {
      _ <- log.info("No ACLs to update")
      _ <- metrics.sendMetric(AclsCreated(0))
      _ <- metrics.sendMetric(AclsRemoved(0))
    } yield () else for {
      _ <- log.info("Updating")
      added = fromSsm -- fromKafka
      removed = fromKafka -- fromSsm
      _ <- log.info(s"To add: $added")
      _ <- log.info(s"To remove: $removed")
      _ <- kafkaAcls.addAcls(groupAcls(added))
      _ <- kafkaAcls.removeAcls(groupAcls(removed))
      _ <- metrics.sendMetric(AclsCreated(added.size))
      _ <- metrics.sendMetric(AclsRemoved(removed.size))
      } yield ()
    } yield ()
}
