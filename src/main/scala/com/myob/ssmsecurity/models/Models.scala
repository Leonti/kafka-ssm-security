package com.myob.ssmsecurity.models

import org.apache.kafka.common.security.scram.ScramCredential

case class SsmParameter(name: String, value: String)

case class ReplicationFactor(value: Int)
case class PartitionCount(value: Int)
case class RetentionHs(value: Long)
case class Topic(name: String,
                 replicationFactor: ReplicationFactor,
                 partitionCount: PartitionCount,
                 retentionHs: Option[RetentionHs])

case class Error(value: String)

case class UserName(value: String)
case class Password(value: String)
case class User(name: UserName, password: Password)

case class StoredUser(name: UserName, credentials: ScramCredential)

sealed trait Metric {
  def value: Int
  def name: String
}
case class UsersCreated(value: Int, name: String = "users_created") extends Metric
case class UsersUpdated(value: Int, name: String = "users_updated") extends Metric
case class UsersRemoved(value: Int, name: String = "users_removed") extends Metric
case class UsersFailed(value: Int, name: String = "users_failed") extends Metric
case class AclsCreated(value: Int, name: String = "acls_created") extends Metric
case class AclsRemoved(value: Int, name: String = "acls_removed") extends Metric
case class AclsFailed(value: Int, name: String = "acls_failed") extends Metric
case class TopicsCreated(value: Int, name: String = "topics_created") extends Metric
case class TopicsFailed(value: Int, name: String = "topics_failed") extends Metric
case class TopicsOutOfSync(value: Int, name: String = "topics_out_of_sync") extends Metric
