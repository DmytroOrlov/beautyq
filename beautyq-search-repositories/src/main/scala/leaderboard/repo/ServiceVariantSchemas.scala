package leaderboard.repo

import distage.Lifecycle
import doobie.Update
import doobie.free.{connection => FC}
import doobie.implicits.*
import izumi.functional.bio.{Error2, F, Primitives2}
import leaderboard.model.{AttributeDefinition, QueryFailure, ServiceId, ServiceVariantSchema, ServiceVariantSchemaItem}
import leaderboard.runtime.QueryFailureToThrowable
import leaderboard.sql.SQL
import logstage.LogIO2
import scala.annotation.unused

trait ServiceVariantSchemas[F[_, _]] {
  def upsertServiceVariantSchema(schema: ServiceVariantSchema): F[QueryFailure, Unit]
  def getServiceVariantSchema(serviceId: ServiceId): F[QueryFailure, ServiceVariantSchema]
}

object ServiceVariantSchemas {
  /** Typed value source for the [[ServiceVariantSchema]] aggregate: keyed by
    * `serviceId`, physically sourced from the [[ServiceVariantSchemaItem]] rows
    * (the physical table stores one row per schema item, so the row source
    * name comes from [[ServiceVariantSchemaItem]], not the aggregate). Derived
    * via [[RepoValueSource.derived]] - the aggregate is not claimed to share
    * the physical item-row columns; only the row type is a real [[RepoEntity]].
    */
  val valueSource: RepoValueSource[ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem] =
    RepoValueSource.derived[ServiceVariantSchema, ServiceId, ServiceVariantSchemaItem](_.serviceId)

  private type ServiceVariantSchemaRow       = (String, Boolean)
  private type ServiceVariantSchemaInsertRow = (ServiceId, String, Boolean)

  private def serviceNotFound(serviceId: ServiceId): QueryFailure =
    QueryFailure.domain(s"Service $serviceId does not exist")

  private def unknownAttributeCode(queryName: String, attributeCode: String): QueryFailure =
    QueryFailure.operation(queryName, s"Unknown MasterServiceOfferVariant attribute code: $attributeCode")

  private def decodeSchemaRow(
    queryName: String,
    row: ServiceVariantSchemaRow,
  ): Either[QueryFailure, ServiceVariantSchemaItem] =
    AttributeDefinition.fromCode(row._1) match {
      case Some(attributeDefinition) =>
        Right(ServiceVariantSchemaItem(attributeDefinition, row._2))
      case None =>
        Left(unknownAttributeCode(queryName, row._1))
    }

  private def liftEither[F[+_, +_]: Error2, A](either: Either[QueryFailure, A]): F[QueryFailure, A] =
    either match {
      case Right(value) =>
        F.pure(value)
      case Left(error) =>
        F.fail(error)
    }

  private def schemaInsertRows(schema: ServiceVariantSchema): List[ServiceVariantSchemaInsertRow] =
    schema.items
      .map {
        item =>
          (schema.serviceId, item.attribute.code, item.required)
      }.toList.sortBy(_._2)

  private def schemaFromRows(
    queryName: String,
    serviceId: ServiceId,
    rows: List[ServiceVariantSchemaRow],
  ): Either[QueryFailure, ServiceVariantSchema] =
    rows
      .foldLeft[Either[QueryFailure, List[ServiceVariantSchemaItem]]](Right(Nil)) {
        case (acc, row) =>
          for {
            current <- acc
            decoded <- decodeSchemaRow(queryName, row)
          } yield decoded :: current
      }.map(items => ServiceVariantSchema.fromItems(serviceId, items.reverse))

  /** Decodes already-selected `(attribute_code, required)` rows into a [[ServiceVariantSchema]], using
    * the exact same decoder [[getServiceVariantSchema]] uses. Exposed narrowly so a transaction-local
    * storage probe can decode rows it selected and deleted itself, without duplicating the decode logic
    * or starting the separate transaction `getServiceVariantSchema` always opens.
    */
  private[leaderboard] def decodeStoredSchema(
    queryName: String,
    serviceId: ServiceId,
    rows: List[(String, Boolean)],
  ): Either[QueryFailure, ServiceVariantSchema] =
    schemaFromRows(queryName, serviceId, rows)

  private def serviceExists[F[+_, +_]](sql: SQL[F])(serviceId: ServiceId): F[QueryFailure, Boolean] =
    sql.execute("service-exists") {
      sql"""
        select exists(
          select 1
          from service
          where id = $serviceId
        )
      """.query[Boolean].unique
    }

  private def insertSchemaRows(rows: List[ServiceVariantSchemaInsertRow]) =
    if (rows.isEmpty) {
      FC.pure(0)
    } else {
      Update[ServiceVariantSchemaInsertRow](
        """insert into service_variant_schema_item (
          |  service_id,
          |  attribute_code,
          |  required
          |)
          |values (?, ?, ?)
          |""".stripMargin
      ).updateMany(rows)
    }

  class Dummy[F[+_, +_]: Error2: Primitives2](
    services: Services[F]
  ) extends Lifecycle.LiftF[F[Nothing, _], ServiceVariantSchemas[F]](
      for {
        state <- F.mkRef(Map.empty[ServiceId, ServiceVariantSchema])
      } yield {
        new ServiceVariantSchemas[F] {
          def upsertServiceVariantSchema(schema: ServiceVariantSchema): F[QueryFailure, Unit] =
            services.getService(schema.serviceId).flatMap {
              case None =>
                F.fail(serviceNotFound(schema.serviceId))
              case Some(_) =>
                state.update_(_ + (schema.serviceId -> schema))
            }

          def getServiceVariantSchema(serviceId: ServiceId): F[QueryFailure, ServiceVariantSchema] =
            state.get.map {
              current =>
                current.getOrElse(serviceId, ServiceVariantSchema.empty(serviceId))
            }
        }
      }
    )

  class Postgres[F[+_, +_]: Error2](
    @unused services: Services[F],
    sql: SQL[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], ServiceVariantSchemas[F]](
      for {
        _ <- log.info("Creating ServiceVariantSchemas table")
        _ <- QueryFailureToThrowable.lift(sql.execute("ddl-service-variant-schema-items") {
          sql"""create table if not exists service_variant_schema_item (
               |  service_id uuid not null,
               |  attribute_code text not null,
               |  required boolean not null,
               |  primary key (service_id, attribute_code),
               |  constraint service_variant_schema_item_service_fk
               |    foreign key (service_id) references service(id)
               |) without oids
               |""".stripMargin.update.run
        })
      } yield new ServiceVariantSchemas[F] {
        def upsertServiceVariantSchema(schema: ServiceVariantSchema): F[QueryFailure, Unit] =
          serviceExists(sql)(schema.serviceId).flatMap {
            exists =>
              if (!exists) {
                F.fail(serviceNotFound(schema.serviceId))
              } else {
                sql
                  .execute("upsert-service-variant-schema") {
                    for {
                      _ <- sql"""delete from service_variant_schema_item
                                |where service_id = ${schema.serviceId}
                                |""".stripMargin.update.run
                      _ <- insertSchemaRows(schemaInsertRows(schema))
                    } yield ()
                  }
                  .void
              }
          }

        def getServiceVariantSchema(serviceId: ServiceId): F[QueryFailure, ServiceVariantSchema] =
          sql
            .execute("get-service-variant-schema") {
              sql"""select attribute_code, required
                   |from service_variant_schema_item
                   |where service_id = $serviceId
                   |order by attribute_code asc
                   |""".stripMargin.query[ServiceVariantSchemaRow].to[List]
            }.flatMap {
              rows =>
                liftEither[F, ServiceVariantSchema](decodeStoredSchema("get-service-variant-schema", serviceId, rows))
            }
      }
    )
}
