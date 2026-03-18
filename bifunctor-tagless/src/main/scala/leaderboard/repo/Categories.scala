package leaderboard.repo

import distage.Lifecycle
import doobie.postgres.implicits.*
import doobie.implicits.*
import izumi.functional.bio.{Applicative2, F, Monad2, Primitives2}
import leaderboard.model.{Category, CategoryId, QueryFailure}
import leaderboard.sql.SQL
import logstage.LogIO2

trait Categories[F[_, _]] {
  def upsertCategory(category: Category): F[QueryFailure, Unit]
  def getCategory(id: CategoryId): F[QueryFailure, Option[Category]]
  def getChildren(parentId: CategoryId): F[QueryFailure, List[Category]]
}

object Categories {

  final class Dummy[F[+_, +_]: Applicative2: Primitives2]
    extends Lifecycle.LiftF[F[Nothing, _], Categories[F]](
      for {
        state <- F.mkRef(Map.empty[CategoryId, Category])
      } yield {
        new Categories[F] {
          override def upsertCategory(category: Category): F[Nothing, Unit] =
            state.update_(_ + (category.id -> category))

          override def getCategory(id: CategoryId): F[Nothing, Option[Category]] =
            state.get.map(_.get(id))

          override def getChildren(parentId: CategoryId): F[Nothing, List[Category]] =
            state.get.map(
              _.values
                .filter(_.parentId == parentId)
                .toList
                .sortBy(c => (c.depth, c.name))
            )
        }
      }
    )

  final class Postgres[F[+_, +_]: Monad2](
    sql: SQL[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], Categories[F]](
      for {
        _ <- log.info("Creating Categories table")
        _ <- sql.execute("ddl-categories") {
          sql"""
            create table if not exists categories (
              id uuid not null,
              parent_id uuid not null,
              depth int not null,
              name text not null,
              primary key (id)
            ) without oids
          """.update.run
        }
        _ <- sql.execute("ddl-categories-parent-id-idx") {
          sql"""
            create index if not exists categories_parent_id_idx
              on categories(parent_id)
          """.update.run
        }
      } yield new Categories[F] {

        override def upsertCategory(category: Category): F[QueryFailure, Unit] = {
          sql
            .execute("upsert-category") {
              sql"""
                insert into categories (id, parent_id, depth, name)
                values (${category.id}, ${category.parentId}, ${category.depth}, ${category.name})
                on conflict (id) do update set
                  parent_id = excluded.parent_id,
                  depth = excluded.depth,
                  name = excluded.name
              """.update.run
            }
            .void
        }

        override def getCategory(id: CategoryId): F[QueryFailure, Option[Category]] = {
          sql.execute("get-category") {
            sql"""
              select id, parent_id, depth, name
              from categories
              where id = $id
            """.query[Category].option
          }
        }

        override def getChildren(parentId: CategoryId): F[QueryFailure, List[Category]] = {
          sql.execute("get-children") {
            sql"""
              select id, parent_id, depth, name
              from categories
              where parent_id = $parentId
              order by depth asc, name asc
            """.query[Category].to[List]
          }
        }
      }
    )
}
