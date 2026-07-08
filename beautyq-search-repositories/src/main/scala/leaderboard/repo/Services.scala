package leaderboard.repo

import distage.Lifecycle
import doobie.Fragment
import doobie.implicits.*
import doobie.postgres.implicits.*
import izumi.functional.bio.{Error2, F, Primitives2}
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import leaderboard.model.{QueryFailure, Service, ServiceId}
import leaderboard.repo.RepoOp.{ManyByKey, OptionalByKey}
import leaderboard.runtime.QueryFailureToThrowable
import leaderboard.sql.SQL
import logstage.LogIO2

import scala.annotation.unused

trait Services[F[_, _]] {
  def upsertService(service: Service): F[QueryFailure, Unit]
  def getService(id: ServiceId): F[QueryFailure, Option[Service]]
  def getServicesByCategory(categoryId: CategoryId): F[QueryFailure, List[Service]]
}

object Services {
  /** Model-derived entity metadata for [[Service]]. */
  val entity: RepoEntity[Service] = RepoEntity.derived[Service]

  /** Optional service by id. */
  def byId[F[_, _]](repo: Services[F]): OptionalByKey[F, ServiceId, Service] =
    OptionalByKey.derived[F, Services[F], ServiceId, Service](repo)

  /** Services by category id. */
  def byCategory[F[_, _]](repo: Services[F]): ManyByKey[F, CategoryId, Service] =
    ManyByKey.derived[F, Services[F], CategoryId, Service](repo)

  private def categoryNotFound(categoryId: CategoryId): QueryFailure =
    QueryFailure.domain(s"Category $categoryId does not exist")

  private def rootCategoryCannotOwnServices: QueryFailure =
    QueryFailure.domain(s"Root category $rootCategoryId is synthetic and must not own services")

  class Dummy[F[+_, +_]: Error2: Primitives2](
    categories: Categories[F]
  ) extends Lifecycle.LiftF[F[Nothing, _], Services[F]](
      for {
        state <- F.mkRef(Map.empty[ServiceId, Service])
      } yield {
        new Services[F] {
          def upsertService(service: Service): F[QueryFailure, Unit] = {
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

          def getService(id: ServiceId): F[QueryFailure, Option[Service]] =
            state.get.map(_.get(id))

          def getServicesByCategory(categoryId: CategoryId): F[QueryFailure, List[Service]] =
            state.get.map(
              _.values
                .filter(_.categoryId == categoryId)
                .toList
                .sortBy(_.name)
            )
        }
      }
    )

  class Postgres[F[+_, +_]: Error2](
    @unused categories: Categories[F],
    sql: SQL[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], Services[F]](
      for {
        _ <- log.info("Creating Services table")
        _ <- QueryFailureToThrowable.lift(sql.execute("ddl-services") {
          Fragment
            .const("""
              create table if not exists service (
                id uuid not null,
                category_id uuid not null,
                name text not null,
                primary key (id),
                constraint service_category_fk
                  foreign key (category_id) references category(id),
                constraint service_category_not_root
                  check (category_id <> %s)
              ) without oids""".formatted(rootCategoryIdSqlLiteral)).update.run
        })
        _ <- QueryFailureToThrowable.lift(sql.execute("ddl-services-category-id-idx") {
          sql"""
            create index if not exists service_category_id_idx
              on service(category_id)
          """.update.run
        })
      } yield new Services[F] {
        def upsertService(service: Service): F[QueryFailure, Unit] = {
          if (service.categoryId == rootCategoryId) {
            F.fail(rootCategoryCannotOwnServices)
          } else {
            categoryExists(service.categoryId).flatMap {
              exists =>
                if (!exists) {
                  F.fail(categoryNotFound(service.categoryId))
                } else {
                  sql
                    .execute("upsert-service") {
                      sql"""
                      insert into service (id, category_id, name)
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

        def getService(id: ServiceId): F[QueryFailure, Option[Service]] =
          sql.execute("get-service") {
            sql"""
              select id, category_id, name
              from service
              where id = $id
            """.query[Service].option
          }

        def getServicesByCategory(categoryId: CategoryId): F[QueryFailure, List[Service]] =
          sql.execute("get-services-by-category") {
            sql"""
              select id, category_id, name
              from service
              where category_id = $categoryId
              order by name asc
            """.query[Service].to[List]
          }

        def categoryExists(categoryId: CategoryId): F[QueryFailure, Boolean] =
          sql.execute("category-exists") {
            sql"""
        select exists(
          select 1
          from category
          where id = $categoryId
        )
      """.query[Boolean].unique
          }
      }
    )
}
