package com.myob.ssmsecurity.interpreters

import cats.Monad
import com.myob.ssmsecurity.algebras.SsmAlg
import com.myob.ssmsecurity.models.SsmParameter

class SsmAlgTest[F[_]: Monad](parameters: SsmParameter*) extends SsmAlg[F] {

  private def map: Map[String, SsmParameter] = parameters.map(p => p.name -> p).toMap

  override def getParameterNames(filter: String): F[Set[String]] = Monad[F].pure(map.keys.toSet)

  override def getParameter(name: String): F[Option[SsmParameter]] = Monad[F].pure(map.get(name))
}
