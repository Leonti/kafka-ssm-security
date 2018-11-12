package com.myob.ssmsecurity.models

import org.apache.kafka.common.security.scram.ScramCredential

case class SsmParameter(name: String, value: String)

case class ReplicationFactor(value: Int)
case class PartitionCount(value: Int)
case class RetentionHs(value: Int)
case class Topic(name: String, replicationFactor: ReplicationFactor, partitionCount: PartitionCount, retentionHs: RetentionHs)

case class Error(value: String)

case class UserName(value: String)
case class Password(value: String)
case class User(name: UserName, password: Password)

case class StoredUser(name: UserName, credentials: ScramCredential)
