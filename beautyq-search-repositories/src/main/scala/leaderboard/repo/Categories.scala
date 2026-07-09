package leaderboard.repo

import distage.Lifecycle
import doobie.Fragment
import doobie.implicits.*
import izumi.functional.bio.{Error2, F, Primitives2}
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import leaderboard.model.{Category, QueryFailure}
import leaderboard.runtime.QueryFailureToThrowable
import leaderboard.sql.SQL
import logstage.LogIO2

trait Categories[F[_, _]] {
  def upsertCategory(category: Category): F[QueryFailure, Unit]
  def getCategory(id: CategoryId): F[QueryFailure, Option[Category]]
  def getChildren(parentId: CategoryId): F[QueryFailure, List[Category]]
}

object Categories {
  /** Model-derived entity metadata for [[Category]]. */
  val entity: RepoEntity[Category] = RepoEntity.derived[Category]

  private def parentNotFound(parentId: CategoryId): QueryFailure =
    QueryFailure.domain(s"Parent category $parentId does not exist")

  private def rootCategoryCannotBePersisted: QueryFailure =
    QueryFailure.domain(s"Root category $rootCategoryId is synthetic and must not be persisted")

  // AI-NOTE: For izumi/distage/BIO typeclasses and Lifecycle patterns used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md
  class Dummy[F[+_, +_]: Error2: Primitives2]
    extends Lifecycle.LiftF[F[Nothing, _], Categories[F]](
      for {
        state <- F.mkRef(Map.empty[CategoryId, Category])
      } yield {
        new Categories[F] {
          def upsertCategory(category: Category): F[QueryFailure, Unit] = {
            if (category.id == rootCategoryId) {
              F.fail(rootCategoryCannotBePersisted)
            } else {
              state
                .modify[Either[QueryFailure, Unit]] {
                  current =>
                    if (category.parentId == rootCategoryId || current.contains(category.parentId)) {
                      Right(()) -> (current + (category.id -> category))
                    } else {
                      Left(parentNotFound(category.parentId)) -> current
                    }
                }.fromEither
            }
          }

          def getCategory(id: CategoryId): F[QueryFailure, Option[Category]] =
            state.get.map(_.get(id))

          def getChildren(parentId: CategoryId): F[QueryFailure, List[Category]] =
            state.get.map(
              _.values
                .filter(_.parentId == parentId)
                .toList
                .sortBy(c => (c.depth, c.name))
            )
        }
      }
    )

  class Postgres[F[+_, +_]: Error2](
    sql: SQL[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], Categories[F]](
      for {
        _ <- log.info("Creating Categories table")
        _ <- QueryFailureToThrowable.lift(sql.execute("ddl-categories") {
          Fragment
            .const("""
          create table if not exists category (
                id uuid not null,
                parent_id uuid not null,
                depth int not null,
                name text not null,
                primary key (id),
                constraint category_not_root
                  check (id <> %s)
              ) without oids""".formatted(rootCategoryIdSqlLiteral)).update.run
        })
        _ <- QueryFailureToThrowable.lift(sql.execute("ddl-categories-parent-id-idx") {
          sql"""
            create index if not exists category_parent_id_idx
              on category(parent_id)
          """.update.run
        })
      } yield new Categories[F] {
        def upsertCategory(category: Category): F[QueryFailure, Unit] = {
          if (category.id == rootCategoryId) {
            F.fail(rootCategoryCannotBePersisted)
          } else {
            categoryExists(category.parentId).flatMap {
              exists =>
                if (!exists) {
                  F.fail(parentNotFound(category.parentId))
                } else {
                  sql
                    .execute("upsert-category") {
                      sql"""
                      insert into category (id, parent_id, depth, name)
                      values (${category.id}, ${category.parentId}, ${category.depth}, ${category.name})
                      on conflict (id) do update set
                        parent_id = excluded.parent_id,
                        depth = excluded.depth,
                        name = excluded.name
                    """.update.run
                    }
                    .void
                }
            }
          }
        }

        def getCategory(id: CategoryId): F[QueryFailure, Option[Category]] = {
          sql.execute("get-category") {
            sql"""
                select id, parent_id, depth, name
                from category
                where id = $id
              """.query[Category].option
          }
        }

        def getChildren(parentId: CategoryId): F[QueryFailure, List[Category]] = {
          sql.execute(if (parentId == rootCategoryId) "get-root-children" else "get-children") {
            sql"""
                select id, parent_id, depth, name
                from category
                where parent_id = $parentId
                order by depth asc, name asc
              """.query[Category].to[List]
          }
        }

        def categoryExists(parentId: CategoryId): F[QueryFailure, Boolean] =
          if (parentId == rootCategoryId) {
            F.pure(true)
          } else {
            sql.execute("category-parent-exists") {
              sql"""
                select exists(
                  select 1
                  from category
                  where id = $parentId
                )
              """.query[Boolean].unique
            }
          }
      }
    )
}
