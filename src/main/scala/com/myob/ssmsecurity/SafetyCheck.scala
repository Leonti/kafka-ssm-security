package com.myob.ssmsecurity

import cats.Monad
import cats.implicits._
import com.myob.ssmsecurity.algebras.{LogAlg, SsmAlg}

class SafetyCheck[F[_]: Monad](clusterName: String,
                               ssm: SsmAlg[F],
                               log: LogAlg[F]) {

  private def ssmConfig: SsmConfig[F] = new SsmConfig[F](clusterName, ssm)

  val isOk: F[Boolean] = for {
    ssmTopics <- ssmConfig.getTopics
    ssmUsers <- ssmConfig.getUsers
    ssmAcls <- ssmConfig.getAcls
    ssmCount = ssmTopics._2.size + ssmUsers._2.size + ssmAcls._2.size
    _ <- if (ssmCount == 0) {
      log.error("SSM parameter store appears to have no valid entries, " +
        "make sure configuration is correct or use '--force' which will remove users and ACLs from the cluster")
    } else {
      Monad[F].pure(())
    }
  } yield ssmCount > 0

}
