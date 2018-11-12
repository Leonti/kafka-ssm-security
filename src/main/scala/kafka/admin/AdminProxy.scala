package kafka.admin

import cats.effect.IO
import com.myob.ssmsecurity.models.{Password, UserName}
import kafka.admin.ConfigCommand.ConfigCommandOptions
import kafka.zk.AdminZkClient

/**
  * AdminProxy is a shim to access the internals of the Kafka admin package for their config command;
  * we just need to be able to call their existing code in Scala instead of in their Bash script.
  *
  * If we were to use the higher level "main" function instead of the private one, it would completely
  * crash our process on any errors with a call to [[kafka.utils.Exit.exit(1)]] that we can't avoid.
  */
object AdminProxy {

  def addUser(userName: UserName, password: Password, adminZkClient: AdminZkClient): IO[Unit] = IO {

    // We need to pass the options like command line params because ConfigCommandOptions is
    // coupled with the assumption of parsing from Bash.
    val opts = new ConfigCommandOptions(Array(
      "--alter",
      "--add-config", s"SCRAM-SHA-256=[iterations=8192,password=${password.value}]",
      "--entity-type", "users",
      "--entity-name", userName.value
    ))

    // Our path doesn't use the zkClient at all on the inside, so we leave it null.
    ConfigCommand.alterConfig(null, opts, adminZkClient)
  }

  def removeUser(userName: UserName, adminZkClient: AdminZkClient):  IO[Unit] = IO {
    val opts = new ConfigCommandOptions(Array(
      "--alter",
      "--delete-config", "SCRAM-SHA-256",
      "--entity-type", "users",
      "--entity-name", userName.value
    ))

    ConfigCommand.alterConfig(null, opts, adminZkClient)
  }
}
