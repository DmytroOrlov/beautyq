package leaderboard.sql

import doobie.free.connection.ConnectionIO
import doobie.util.transactor.Transactor
import izumi.functional.bio.Panic2
import izumi.functional.bio.catz.*
import leaderboard.model.QueryFailure

trait SQL[F[_, _]] {
  def execute[A](queryName: String)(conn: ConnectionIO[A]): F[QueryFailure, A]
}

object SQL {
  // AI-NOTE: For Panic2 and error-channel patterns used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md
  final class Impl[F[+_, +_]: Panic2](
    transactor: Transactor[F[Throwable, _]]
  ) extends SQL[F] {
    def execute[A](queryName: String)(conn: ConnectionIO[A]): F[QueryFailure, A] = {
      transactor.trans
        .apply(conn)
        .leftMap(QueryFailure(queryName, _))
    }
  }
}
