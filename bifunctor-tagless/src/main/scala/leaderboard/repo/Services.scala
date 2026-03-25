package leaderboard.repo

import distage.Lifecycle
import doobie.implicits.*
import doobie.postgres.implicits.*
import izumi.functional.bio.{Error2, F, Primitives2}
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import leaderboard.model.{QueryFailure, Service, ServiceId}
import leaderboard.repo.Categories.categoryExists
import leaderboard.sql.SQL
import logstage.LogIO2

trait Services[F[_, _]] {
  def upsertService(service: Service): F[QueryFailure, Unit]
  def getService(id: ServiceId): F[QueryFailure, Option[Service]]
  def getServicesByCategory(categoryId: CategoryId): F[QueryFailure, List[Service]]
}

object Services {
  private def categoryNotFound(categoryId: CategoryId): QueryFailure =
    QueryFailure("no query", new Exception(s"Category $categoryId does not exist"))

  private def rootCategoryCannotOwnServices: QueryFailure =
    QueryFailure("no query", new Exception(s"Root category $rootCategoryId is synthetic and must not own services"))

  final class Dummy[F[+_, +_]: Error2: Primitives2](
    categories: Categories[F]
  ) extends Lifecycle.LiftF[F[QueryFailure, _], Services[F]](
      for {
        state <- F.mkRef(Map.empty[ServiceId, Service])
      } yield {
        new Services[F] {
          override def upsertService(service: Service): F[QueryFailure, Unit] = {
            if (service.categoryId == rootCategoryId) {
              F.fail(rootCategoryCannotOwnServices)
            } else {
              categories.getCategory(service.categoryId).flatMap {
                case Some(_) =>
                  state.update_(_ + (service.id -> service))
                case None =>
                  F.fail(categoryNotFound(service.categoryId))
              }
            }
          }

          override def getService(id: ServiceId): F[QueryFailure, Option[Service]] =
            state.get.map(_.get(id))

          override def getServicesByCategory(categoryId: CategoryId): F[QueryFailure, List[Service]] =
            state.get.map(
              _.values
                .filter(_.categoryId == categoryId)
                .toList
                .sortBy(_.name)
            )
        }
      }
    )

  final class Postgres[F[+_, +_]: Error2](
    sql: SQL[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], Services[F]](
      for {
        _ <- log.info("Creating Services table")
        _ <- sql.execute("ddl-services") {
          sql"""
            create table if not exists services (
              id uuid not null,
              category_id uuid not null,
              name text not null,
              primary key (id),
              constraint services_category_fk
                foreign key (category_id) references categories(id),
              constraint services_category_not_root
                check (category_id <> $rootCategoryId)
            ) without oids
          """.update.run
        }
        _ <- sql.execute("ddl-services-category-id-idx") {
          sql"""
            create index if not exists services_category_id_idx
              on services(category_id)
          """.update.run
        }
      } yield new Services[F] {

        override def upsertService(service: Service): F[QueryFailure, Unit] = {
          if (service.categoryId == rootCategoryId) {
            F.fail(rootCategoryCannotOwnServices)
          } else {
            categoryExists(sql)(service.categoryId).flatMap {
              exists =>
                if (!exists) {
                  F.fail(categoryNotFound(service.categoryId))
                } else {
                  sql
                    .execute("upsert-service") {
                      sql"""
                      insert into services (id, category_id, name)
                      values (${service.id}, ${service.categoryId}, ${service.name})
                      on conflict (id) do update set
                        category_id = excluded.category_id,
                        name = excluded.name
                    """.update.run
                    }.void
                }
            }
          }
        }

        override def getService(id: ServiceId): F[QueryFailure, Option[Service]] =
          sql.execute("get-service") {
            sql"""
              select id, category_id, name
              from services
              where id = $id
            """.query[Service].option
          }

        override def getServicesByCategory(categoryId: CategoryId): F[QueryFailure, List[Service]] =
          sql.execute("get-services-by-category") {
            sql"""
              select id, category_id, name
              from services
              where category_id = $categoryId
              order by name asc
            """.query[Service].to[List]
          }
      }
    )
}
