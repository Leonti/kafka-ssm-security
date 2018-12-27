import cats.effect._
import cats.syntax.all._
import com.amazonaws.regions.Regions
import com.myob.ssmsecurity._
import com.myob.ssmsecurity.interpreters._
import kafka.zk.{AdminZkClient, KafkaZkClient}
import org.apache.kafka.common.security.JaasUtils
import org.apache.kafka.common.utils.Time

import scala.concurrent.duration._

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    val zkEndpoint = sys.env.getOrElse("ZK_ENDPOINT", "localhost:2181")
    val clusterName = sys.env.getOrElse("CLUSTER_NAME", "example-cluster")

    args match {
      case "sync"::tail => sync(zkEndpoint, clusterName, tail == List("--force"))
      case _ => dump(zkEndpoint, clusterName)
    }
  }

  private def sync(zkEndpoint: String, clusterName: String, force: Boolean): IO[ExitCode] = {
    val region = Regions.fromName(sys.env.getOrElse("REGION", "ap-southeast-2"))
    val metricsNamespace = sys.env.getOrElse("METRICS_NAMESPACE", "KSS")
    val logGroup = sys.env.getOrElse("LOG_GROUP", "kafka-ssm-security")
    val logStream = sys.env.getOrElse("LOG_STREAM", "logs")

    val logger = new AWSLogger(Regions.AP_SOUTHEAST_2, logGroup, logStream)
    val metrics = new CloudWatch(region, metricsNamespace)
    val ssm = new SsmAws(region)

    val safetyCheck = new SafetyCheck[IO](clusterName, ssm, logger)

    val aclSync = new AclSync[IO](clusterName, new KafkaAcls(zkEndpoint), ssm, metrics, logger)

    val zkClient = KafkaZkClient(zkEndpoint, JaasUtils.isZkSecurityEnabled, 30000, 30000,
      Int.MaxValue, Time.SYSTEM)

    val topicSync = new TopicSync[IO](clusterName, new KafkaTopics(zkClient), ssm, metrics, logger)

    val adminZkClient = new AdminZkClient(zkClient)
    val userSync = new UserSync[IO](clusterName, new KafkaUsers(adminZkClient), ssm, metrics, logger)

    val sync = for {
      isOk <- if (force) IO.pure(true) else safetyCheck.isOk
      _ <- if (isOk) {
        for {
          _ <- aclSync.sync
          _ <- topicSync.sync
          _ <- userSync.sync
        } yield ()
      } else IO.pure(())
    } yield ()

    def loop(io: IO[Unit])(implicit timer: Timer[IO]): IO[Unit] = {
      io.flatMap(_ => IO.sleep(1.minute) *> loop(io))
        .handleErrorWith(err => for {
          _ <- logger.error(err.getMessage)
          _ <- IO.sleep(1.minute) *> loop(io)
        } yield ())
    }

    loop(sync).as(ExitCode.Success)
  }

  private def dump(zkEndpoint: String, clusterName: String): IO[ExitCode] = {
    val zkClient = KafkaZkClient(zkEndpoint, JaasUtils.isZkSecurityEnabled, 30000, 30000,
      Int.MaxValue, Time.SYSTEM)

    val adminZkClient = new AdminZkClient(zkClient)

    val dump = new CloudFormationDump(clusterName,
      new KafkaTopics(zkClient),
      new KafkaUsers(adminZkClient),
      new KafkaAcls(zkEndpoint)
    )

    dump.dump.flatMap(cf => IO(println(cf))).as(ExitCode.Success)
  }

}
