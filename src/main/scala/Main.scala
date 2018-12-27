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
    val region = Regions.fromName(sys.env.getOrElse("REGION", "ap-southeast-2"))
    val metricsNamespace = sys.env.getOrElse("METRICS_NAMESPACE", "KSS")
    val logGroup = sys.env.getOrElse("LOG_GROUP", "kafka-ssm-security")
    val logStream = sys.env.getOrElse("LOG_STREAM", "logs")

    val logger = new AWSLogger(Regions.AP_SOUTHEAST_2, logGroup, logStream)
    val metrics = new CloudWatch(region, metricsNamespace)
    val ssm = new SsmAws(region)

    val aclSync = new AclSync[IO](clusterName, new KafkaAcls(zkEndpoint), ssm, metrics, logger)

    val zkClient = KafkaZkClient(zkEndpoint, JaasUtils.isZkSecurityEnabled, 30000, 30000,
      Int.MaxValue, Time.SYSTEM)
    val adminZkClient = new AdminZkClient(zkClient)
    val topicSync = new TopicSync[IO](clusterName, new KafkaTopics(adminZkClient), ssm, metrics, logger)

    val userSync = new UserSync[IO](clusterName, new KafkaUsers(adminZkClient), ssm, metrics, logger)

    val sync = for {
      _ <- aclSync.sync
      _ <- topicSync.sync
      _ <- userSync.sync
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
}
