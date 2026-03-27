package leaderboard.repo

import distage.Lifecycle
import doobie.Update
import doobie.free.{connection => FC}
import doobie.implicits.*
import doobie.postgres.implicits.*
import izumi.functional.bio.{Error2, F, Primitives2}
import leaderboard.model.{MasterServiceOfferVariantAttributeDefinition, QueryFailure, ServiceId, ServiceVariantSchema}
import leaderboard.sql.SQL
import logstage.LogIO2
import scala.annotation.unused

trait ServiceVariantSchemas[F[_, _]] {
  def upsertServiceVariantSchema(schema: ServiceVariantSchema): F[QueryFailure, Unit]
  def getServiceVariantSchema(serviceId: ServiceId): F[QueryFailure, ServiceVariantSchema]
}

object ServiceVariantSchemas {
  private type ServiceVariantSchemaRow = (String, Boolean)
  private type ServiceVariantSchemaInsertRow = (ServiceId, String, Boolean)

  private def serviceNotFound(serviceId: ServiceId): QueryFailure =
    QueryFailure("no query", new Exception(s"Service $serviceId does not exist"))

  private def unknownAttributeCode(queryName: String, attributeCode: String): QueryFailure =
    QueryFailure(queryName, new Exception(s"Unknown MasterServiceOfferVariant attribute code: $attributeCode"))

  private def decodeSchemaRow(
    queryName: String,
    row: ServiceVariantSchemaRow,
  ): Either[QueryFailure, (MasterServiceOfferVariantAttributeDefinition, Boolean)] =
    MasterServiceOfferVariantAttributeDefinition.fromCode(row._1) match {
      case Some(attributeDefinition) =>
        Right(attributeDefinition -> row._2)
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
    schema.items.iterator.map {
      case (attribute, required) =>
        (schema.serviceId, attribute.code, required)
    }.toList.sortBy(_._2)

  private def schemaFromRows(
    queryName: String,
    serviceId: ServiceId,
    rows: List[ServiceVariantSchemaRow],
  ): Either[QueryFailure, ServiceVariantSchema] =
    rows.foldLeft[Either[QueryFailure, Map[MasterServiceOfferVariantAttributeDefinition, Boolean]]](Right(Map.empty)) {
      case (acc, row) =>
        for {
          current <- acc
          decoded <- decodeSchemaRow(queryName, row)
        } yield current + decoded
    }.map(items => ServiceVariantSchema(serviceId, items))

  private def serviceExists[F[+_, +_]](sql: SQL[F])(serviceId: ServiceId): F[QueryFailure, Boolean] =
    sql.execute("service-exists") {
      sql"""
        select exists(
          select 1
          from services
          where id = $serviceId
        )
      """.query[Boolean].unique
    }

  private def insertSchemaRows(rows: List[ServiceVariantSchemaInsertRow]) =
    if (rows.isEmpty) {
      FC.pure(0)
    } else {
      Update[ServiceVariantSchemaInsertRow](
        """insert into service_variant_schema_items (
          |  service_id,
          |  attribute_code,
          |  required
          |)
          |values (?, ?, ?)
          |""".stripMargin
      ).updateMany(rows)
    }

  final class Dummy[F[+_, +_]: Error2: Primitives2](
    services: Services[F]
  ) extends Lifecycle.LiftF[F[QueryFailure, _], ServiceVariantSchemas[F]](
      for {
        state <- F.mkRef(Map.empty[ServiceId, ServiceVariantSchema])
      } yield {
        new ServiceVariantSchemas[F] {
          override def upsertServiceVariantSchema(schema: ServiceVariantSchema): F[QueryFailure, Unit] =
            services.getService(schema.serviceId).flatMap {
              case None =>
                F.fail(serviceNotFound(schema.serviceId))
              case Some(_) =>
                state.update_(_ + (schema.serviceId -> schema))
            }

          override def getServiceVariantSchema(serviceId: ServiceId): F[QueryFailure, ServiceVariantSchema] =
            state.get.map { current =>
              current.getOrElse(serviceId, ServiceVariantSchema.empty(serviceId))
            }
        }
      }
    )

  final class Postgres[F[+_, +_]: Error2](
    @unused services: Services[F],
    sql: SQL[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], ServiceVariantSchemas[F]](
      for {
        _ <- log.info("Creating ServiceVariantSchemas table")
        _ <- sql.execute("ddl-service-variant-schema-items") {
          sql"""create table if not exists service_variant_schema_items (
               |  service_id uuid not null,
               |  attribute_code text not null,
               |  required boolean not null,
               |  primary key (service_id, attribute_code),
               |  constraint service_variant_schema_items_service_fk
               |    foreign key (service_id) references services(id)
               |) without oids
               |""".stripMargin.update.run
        }
      } yield new ServiceVariantSchemas[F] {
        override def upsertServiceVariantSchema(schema: ServiceVariantSchema): F[QueryFailure, Unit] =
          serviceExists(sql)(schema.serviceId).flatMap {
            exists =>
              if (!exists) {
                F.fail(serviceNotFound(schema.serviceId))
              } else {
                sql
                  .execute("upsert-service-variant-schema") {
                    for {
                      _ <- sql"""delete from service_variant_schema_items
                                 |where service_id = ${schema.serviceId}
                                 |""".stripMargin.update.run
                      _ <- insertSchemaRows(schemaInsertRows(schema))
                    } yield ()
                  }
                  .void
              }
          }

        override def getServiceVariantSchema(serviceId: ServiceId): F[QueryFailure, ServiceVariantSchema] =
          sql.execute("get-service-variant-schema") {
            sql"""select attribute_code, required
                 |from service_variant_schema_items
                 |where service_id = $serviceId
                 |order by attribute_code asc
                 |""".stripMargin.query[ServiceVariantSchemaRow].to[List]
          }.flatMap {
            rows =>
              liftEither[F, ServiceVariantSchema](schemaFromRows("get-service-variant-schema", serviceId, rows))
          }
      }
    )
}
