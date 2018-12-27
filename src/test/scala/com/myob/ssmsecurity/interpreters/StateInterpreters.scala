package com.myob.ssmsecurity.interpreters

import cats.data.State
import cats.implicits._
import com.myob.ssmsecurity.algebras._
import com.myob.ssmsecurity.models._
import kafka.security.auth.{Acl, Resource}

object StateInterpreters {

  case class SystemState(
                          usersAdded: List[User] = List(),
                          usersUpdated: List[User] = List(),
                          userNamesRemoved: List[UserName] = List(),
                          topics: List[Topic] = List(),
                          infos: List[String] = List(),
                          errors: List[String] = List(),
                          metricsSent: List[Metric] = List(),
                          aclsAdded: Map[Resource, Set[Acl]] = Map(),
                          aclsRemoved: Map[Resource, Set[Acl]] = Map())

  type TestProgram[A] = State[SystemState, A]

  class KafkaTopicsAlgState(topicNames: Set[String]) extends KafkaTopicsAlg[TestProgram] {

    override def createTopic(topic: Topic): TestProgram[Unit] = State { st =>
      (st.copy(topics = topic :: st.topics), ())
    }

    override def getTopics: TestProgram[Set[Topic]] = State.pure(topicNames
      .map(name => Topic(name, ReplicationFactor(1), PartitionCount(6), Some(RetentionHs(24)))))
  }

  class LogAlgState extends LogAlg[TestProgram] {
    override def info(message: String): TestProgram[Unit] = State { st =>
      (st.copy(infos = message :: st.infos), ())
    }
    override def error(message: String): TestProgram[Unit] = State { st =>
      (st.copy(errors = message :: st.errors), ())
    }
  }

  class MetricsAlgState extends MetricsAlg[TestProgram] {
    override def sendMetric(metric: Metric): TestProgram[Unit] = State { st =>
      (st.copy(metricsSent = metric :: st.metricsSent), ())
    }
  }

  class KafkaAclsAlgState(acls: Map[Resource, Set[Acl]]) extends KafkaAclsAlg[TestProgram] {
    override def getKafkaAcls: TestProgram[Map[Resource, Set[Acl]]] = State.pure(acls)

    override def addAcls(acls: Map[Resource, Set[Acl]]): TestProgram[Unit] = State { st =>
      (st.copy(aclsAdded = st.aclsAdded.combine(acls)), ())
    }

    override def removeAcls(acls: Map[Resource, Set[Acl]]): TestProgram[Unit] = State { st =>
      (st.copy(aclsRemoved = st.aclsRemoved.combine(acls)), ())
    }
  }

  class KafkaUsersAlgState(storedUsers: Set[StoredUser]) extends KafkaUsersAlg[TestProgram] {
    override def createUser(user: User): TestProgram[Unit] = State { st =>
      (st.copy(usersAdded = user :: st.usersAdded), ())
    }

    override def updateUser(user: User): TestProgram[Unit] = State { st =>
      (st.copy(usersUpdated = user :: st.usersUpdated), ())
    }

    override def getStoredUsers: TestProgram[Set[StoredUser]] = State.pure(storedUsers)

    override def removeUser(userName: UserName): TestProgram[Unit] = State { st =>
      (st.copy(userNamesRemoved = userName :: st.userNamesRemoved), ())
    }
  }
}
