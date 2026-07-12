package leaderboard.sql

import doobie.Fragment
import doobie.free.connection.ConnectionIO
import doobie.util.transactor.Transactor
import izumi.functional.bio.Panic2
import izumi.functional.bio.catz.*
import leaderboard.model.QueryFailure

trait SQL[F[_, _]] {
  def execute[A](queryName: String)(conn: ConnectionIO[A]): F[QueryFailure, A]

  /** Runs `conn` as the only statement sequence of one read-only, repeatable-read transaction: every
    * source `SELECT` inside `conn` observes the same consistent snapshot, unlike calling [[execute]]
    * once per read (which opens one transaction per call and cannot guarantee cross-read consistency).
    */
  def readOnlyRepeatableRead[A](queryName: String)(conn: ConnectionIO[A]): F[QueryFailure, A]
}

object SQL {
  // AI-NOTE: For Panic2 and error-channel patterns used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md
  class Impl[F[+_, +_]: Panic2](
    transactor: Transactor[F[Throwable, _]]
  ) extends SQL[F] {
    def execute[A](queryName: String)(conn: ConnectionIO[A]): F[QueryFailure, A] = {
      transactor.trans
        .apply(conn)
        .leftMap(QueryFailure.fromThrowable(queryName, _))
    }

    def readOnlyRepeatableRead[A](queryName: String)(conn: ConnectionIO[A]): F[QueryFailure, A] = {
      val guarded: ConnectionIO[A] =
        Fragment.const("set transaction isolation level repeatable read, read only").update.run.flatMap(_ => conn)

      transactor.trans
        .apply(guarded)
        .leftMap(QueryFailure.fromThrowable(queryName, _))
    }
  }
}
