package com.myob.ssmsecurity.interpreters

import cats.effect.IO
import com.myob.ssmsecurity.algebras.LogAlg

class Logger extends LogAlg[IO] {
  override def info(message: String): IO[Unit] = IO(println(s"INFO: $message"))
  override def error(message: String): IO[Unit] = IO(println(s"ERROR: $message"))
}
