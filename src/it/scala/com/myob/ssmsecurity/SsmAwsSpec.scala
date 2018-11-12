package com.myob.ssmsecurity

import com.amazonaws.regions.Regions
import com.myob.ssmsecurity.interpreters.SsmAws
import org.scalatest.{FlatSpec, Matchers}

class SsmAwsSpec extends FlatSpec with Matchers {

  behavior of "get parameter names"

  it should "return topics for a cluster" in {
    val ssmAws = new SsmAws(Regions.AP_SOUTHEAST_2)

    val topics = ssmAws.getParameterNames("/kafka-security/ci-cluster/topics").unsafeRunSync()

    topics shouldBe Set("/kafka-security/ci-cluster/topics/test-topic", "/kafka-security/ci-cluster/topics/test-topic2")
  }

  it should "return users for a cluster" in {
    val ssmAws = new SsmAws(Regions.AP_SOUTHEAST_2)

    val users = ssmAws.getParameterNames("/kafka-security/ci-cluster/users").unsafeRunSync()

    users shouldBe Set("/kafka-security/ci-cluster/users/test-user")
  }

  behavior of "get parameter"

  it should "return an existing parameter value" in {
    val ssmAws = new SsmAws(Regions.AP_SOUTHEAST_2)

    val userParameter = ssmAws.getParameter("/kafka-security/ci-cluster/users/test-user").unsafeRunSync()

    userParameter.map(_.value.split("\n").length) shouldBe Some(2)
  }

  it should "return None if parameter doesn't exist" in {
    val ssmAws = new SsmAws(Regions.AP_SOUTHEAST_2)

    val nonExisting = ssmAws.getParameter("/kafka-security/ci-cluster/users/non-existing").unsafeRunSync()

    nonExisting shouldBe None
  }
}
