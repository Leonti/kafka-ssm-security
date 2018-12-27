package com.myob.ssmsecurity.interpreters

import cats.effect.IO
import com.amazonaws.regions.Regions
import com.amazonaws.services.logs.AWSLogsClientBuilder
import com.amazonaws.services.logs.model.{DescribeLogStreamsRequest, InputLogEvent, PutLogEventsRequest}
import com.myob.ssmsecurity.algebras.LogAlg
import scala.collection.JavaConverters._

class AWSLogger(region: Regions, logGroup: String, logStream: String) extends LogAlg[IO] {
  private val awslogs = AWSLogsClientBuilder.standard.withRegion(region).build()

  private def nextSequenceToken: IO[String] = IO(awslogs.describeLogStreams(new DescribeLogStreamsRequest()
    .withLogGroupName(logGroup)
    .withLogStreamNamePrefix(logStream)
  )).map(_.getLogStreams.asScala.filter(_.getLogStreamName == logStream))
    .flatMap(logStreams => if (logStreams.nonEmpty)
      IO(logStreams.head.getUploadSequenceToken) else
      IO.raiseError(new Exception(s"Log stream `$logStream` was not found in log group `$logGroup`. " +
        s"Make sure log group and log stream have been created."))
    )

  private def sendMessage(message: String): IO[Unit] = for {
    _ <- IO (println(message))
    uploadSequenceToken <- nextSequenceToken
    _ <- IO {
      awslogs.putLogEvents(new PutLogEventsRequest()
        .withLogGroupName(logGroup)
        .withLogStreamName(logStream)
        .withSequenceToken(uploadSequenceToken)
        .withLogEvents(new InputLogEvent()
          .withMessage(message)
          .withTimestamp(System.currentTimeMillis())
        ))
    }
  } yield ()

  override def info(message: String): IO[Unit] = sendMessage(s"INFO: $message")
  override def error(message: String): IO[Unit] = sendMessage(s"ERROR: $message")
}
