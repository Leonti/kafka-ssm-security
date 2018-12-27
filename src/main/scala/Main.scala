import cats.effect._
import cats.syntax.all._
import com.myob.ssmsecurity._
import com.myob.ssmsecurity.interpreters._
import kafka.zk.{AdminZkClient, KafkaZkClient}
import org.apache.kafka.common.security.JaasUtils
import org.apache.kafka.common.utils.Time

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    val zkEndpoint = sys.env.getOrElse("ZK_ENDPOINT", "localhost:2181")
    val clusterName = sys.env.getOrElse("CLUSTER_NAME", "example-cluster")
    /*
        val region = Regions.fromName(sys.env.getOrElse("REGION", "ap-southeast-2"))
        val metricsNamespace = sys.env.getOrElse("METRICS_NAMESPACE", "KSS")
        val logGroup = sys.env.getOrElse("LOG_GROUP", "kafka-ssm-security")
        val logStream = sys.env.getOrElse("LOG_STREAM", "logs")

        val logger = new AWSLogger(Regions.AP_SOUTHEAST_2, logGroup, logStream)
        val metrics = new CloudWatch(region, metricsNamespace)
        val ssm = new SsmAws(region)
    */
    dump(zkEndpoint, clusterName)
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
