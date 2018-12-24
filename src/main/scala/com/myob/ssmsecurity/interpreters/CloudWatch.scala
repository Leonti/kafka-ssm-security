package com.myob.ssmsecurity.interpreters

import cats.effect.IO
import com.amazonaws.regions.Regions
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder
import com.amazonaws.services.cloudwatch.model.{MetricDatum, PutMetricDataRequest, StandardUnit}
import com.myob.ssmsecurity.algebras.MetricsAlg
import com.myob.ssmsecurity.models.Metric

class CloudWatch(region: Regions) extends MetricsAlg[IO] {
  private val cw = AmazonCloudWatchClientBuilder.standard.withRegion(region).build()
  private val NAMESPACE = "KSS"

  override def sendMetric(metric: Metric): IO[Unit] = IO {
    val datum = new MetricDatum()
      .withMetricName(metric.name)
      .withUnit(StandardUnit.None)
      .withValue(metric.value.toDouble)

    IO(cw.putMetricData(new PutMetricDataRequest()
      .withNamespace(NAMESPACE)
      .withMetricData(datum)))
  }
}
