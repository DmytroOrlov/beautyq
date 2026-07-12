package leaderboard.repo

import distage.Lifecycle
import doobie.Fragment
import doobie.implicits.*
import izumi.functional.bio.{Error2, F, Primitives2}
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import leaderboard.model.{QueryFailure, Service, ServiceCode, ServiceId}
import leaderboard.runtime.QueryFailureToThrowable
import leaderboard.sql.SQL
import logstage.LogIO2

import scala.annotation.unused

trait Services[F[_, _]] {
  def upsertService(service: Service): F[QueryFailure, Unit]
  def getService(id: ServiceId): F[QueryFailure, Option[Service]]
  def getServiceByCode(code: ServiceCode): F[QueryFailure, Option[Service]]
  def getServicesByCategory(categoryId: CategoryId): F[QueryFailure, List[Service]]
}

object Services {
  /** Model-derived entity metadata for [[Service]]. */
  val entity: RepoEntity[Service] = RepoEntity.derived[Service]

  private def categoryNotFound(categoryId: CategoryId): QueryFailure =
    QueryFailure.domain(s"Category $categoryId does not exist")

  private def rootCategoryCannotOwnServices: QueryFailure =
    QueryFailure.domain(s"Root category $rootCategoryId is synthetic and must not own services")

  private def duplicateServiceCode(code: ServiceCode, existingId: ServiceId): QueryFailure =
    QueryFailure.domain(s"Service code '${code.value}' is already used by service $existingId")

  private def immutableServiceCode(id: ServiceId, existingCode: ServiceCode, requestedCode: ServiceCode): QueryFailure =
    QueryFailure.domain(s"Service $id code is immutable: existing '${existingCode.value}', requested '${requestedCode.value}'")

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
                  state
                    .modify[Either[QueryFailure, Unit]] {
                      current =>
                        val immutabilityViolation: Option[QueryFailure] =
                          current.get(service.id) match {
                            case Some(existing) if existing.code != service.code =>
                              Some(immutableServiceCode(service.id, existing.code, service.code))
                            case _ =>
                              None
                          }

                        val collisionViolation: Option[QueryFailure] =
                          current.values.find(other => other.id != service.id && other.code == service.code) match {
                            case Some(other) => Some(duplicateServiceCode(service.code, other.id))
                            case None        => None
                          }

                        immutabilityViolation.orElse(collisionViolation) match {
                          case Some(failure) => Left(failure) -> current
                          case None          => Right(()) -> (current + (service.id -> service))
                        }
                    }.fromEither
                case None =>
                  F.fail(categoryNotFound(service.categoryId))
              }
            }
          }

          def getService(id: ServiceId): F[QueryFailure, Option[Service]] =
            state.get.map(_.get(id))

          def getServiceByCode(code: ServiceCode): F[QueryFailure, Option[Service]] =
            state.get.map(_.values.find(_.code == code))

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
                code text not null,
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
        _ <- QueryFailureToThrowable.lift(sql.execute("service-code-unique-index") {
          sql"""
            create unique index if not exists service_code_uidx
              on service(code)
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
                  getService(service.id).flatMap {
                    case Some(existing) if existing.code != service.code =>
                      F.fail(immutableServiceCode(service.id, existing.code, service.code))
                    case _ =>
                      getServiceByCode(service.code).flatMap {
                        case Some(other) if other.id != service.id =>
                          F.fail(duplicateServiceCode(service.code, other.id))
                        case _ =>
                          sql
                            .execute("upsert-service") {
                              sql"""
                              insert into service (id, code, category_id, name)
                              values (${service.id}, ${service.code}, ${service.categoryId}, ${service.name})
                              on conflict (id) do update set
                                category_id = excluded.category_id,
                                name = excluded.name
                            """.update.run
                            }.void
                      }
                  }
                }
            }
          }
        }

        def getService(id: ServiceId): F[QueryFailure, Option[Service]] =
          sql.execute("get-service") {
            sql"""
              select id, code, category_id, name
              from service
              where id = $id
            """.query[Service].option
          }

        def getServiceByCode(code: ServiceCode): F[QueryFailure, Option[Service]] =
          sql.execute("get-service-by-code") {
            sql"""
              select id, code, category_id, name
              from service
              where code = $code
            """.query[Service].option
          }

        def getServicesByCategory(categoryId: CategoryId): F[QueryFailure, List[Service]] =
          sql.execute("get-services-by-category") {
            sql"""
              select id, code, category_id, name
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
