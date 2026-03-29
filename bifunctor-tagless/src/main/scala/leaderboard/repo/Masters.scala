package leaderboard.repo

import distage.Lifecycle
import doobie.postgres.implicits.*
import doobie.implicits.*
import izumi.functional.bio.{Applicative2, Error2, F, Primitives2}
import leaderboard.model.{Master, MasterId, QueryFailure}
import leaderboard.sql.SQL
import leaderboard.runtime.QueryFailureToThrowable
import logstage.LogIO2

trait Masters[F[_, _]] {
  def upsertMaster(master: Master): F[QueryFailure, Unit]
  def getMaster(id: MasterId): F[QueryFailure, Option[Master]]
  def getMasters(): F[QueryFailure, List[Master]]
}

object Masters {
  class Dummy[F[+_, +_]: Applicative2: Primitives2]
    extends Lifecycle.LiftF[F[Nothing, _], Masters[F]](for {
      state <- F.mkRef(Map.empty[MasterId, Master])
    } yield {
      new Masters[F] {
        def upsertMaster(master: Master): F[Nothing, Unit] =
          state.update_(_ + (master.id -> master))

        def getMaster(id: MasterId): F[Nothing, Option[Master]] =
          state.get.map(_.get(id))

        def getMasters(): F[Nothing, List[Master]] =
          state.get.map(
            _.values
              .toList
              .sortBy(master => (master.name, master.id.toString))
          )
      }
    })

  class Postgres[F[+_, +_]: Error2](
    sql: SQL[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], Masters[F]](for {
      _ <- log.info("Creating Masters table")
      _ <- QueryFailureToThrowable.lift(sql.execute("ddl-masters") {
        sql"""create table if not exists masters (
             |  id uuid not null,
             |  name text not null,
             |  primary key (id)
             |) without oids
             |""".stripMargin.update.run
      })
    } yield new Masters[F] {
      def upsertMaster(master: Master): F[QueryFailure, Unit] =
        sql
          .execute("upsert-master") {
            sql"""insert into masters (id, name)
                 |values (${master.id}, ${master.name})
                 |on conflict (id) do update set
                 |  name = excluded.name
                 |""".stripMargin.update.run
          }.void

      def getMaster(id: MasterId): F[QueryFailure, Option[Master]] =
        sql.execute("get-master") {
          sql"""select id, name from masters
               |where id = $id
               |""".stripMargin.query[Master].option
        }

      def getMasters(): F[QueryFailure, List[Master]] =
        sql.execute("get-masters") {
          sql"""select id, name from masters
               |order by name asc, id asc
               |""".stripMargin.query[Master].to[List]
        }
    })
}
