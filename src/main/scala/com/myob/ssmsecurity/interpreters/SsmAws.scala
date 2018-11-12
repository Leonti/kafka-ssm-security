package com.myob.ssmsecurity.interpreters

import cats.effect.IO
import com.amazonaws.regions.Regions
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model._
import com.myob.ssmsecurity.algebras.SsmAlg
import com.myob.ssmsecurity.models.SsmParameter

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class SsmAws(region: Regions) extends SsmAlg[IO] {
  private val ssm = AWSSimpleSystemsManagementClientBuilder.standard().withRegion(region).build()

  override def getParameterNames(filter: String): IO[Set[String]] = IO {
    ssm.describeParameters(new DescribeParametersRequest()
      .withFilters(new ParametersFilter()
        .withKey(ParametersFilterKey.Name)
        .withValues(filter)
      )).getParameters.asScala.map(_.getName).toSet
  }

  override def getParameter(name: String): IO[Option[SsmParameter]] =
    IO(Try(ssm.getParameter(new GetParameterRequest().withName(name)
      .withWithDecryption(true)))
      .map(_.getParameter)
      .map(p => SsmParameter(p.getName, p.getValue)))
      .flatMap {
        case Success(p)                             => IO.pure(Some(p))
        case Failure(_: ParameterNotFoundException) => IO.pure(None)
        case Failure(t)                             => IO.raiseError(t)
      }
}
