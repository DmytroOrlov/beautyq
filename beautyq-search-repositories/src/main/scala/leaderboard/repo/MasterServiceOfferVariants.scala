package leaderboard.repo

import distage.Lifecycle
import doobie.free.{connection => FC}
import doobie.implicits.*
import doobie.postgres.implicits.*
import izumi.functional.bio.{Error2, F, Primitives2}
import leaderboard.model.ServiceVariantSchemaValidationError.{DisallowedAttribute, MissingRequiredAttribute}
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariant, MasterServiceOfferVariantAttributes, MasterServiceOfferVariantId, QueryFailure, ServiceId, ServiceVariantSchema}
import leaderboard.repo.RepoOp.{ManyByKey, OptionalByKey}
import leaderboard.runtime.QueryFailureToThrowable
import leaderboard.sql.SQL
import logstage.LogIO2
import scala.annotation.unused

trait MasterServiceOfferVariants[F[_, _]] {
  def upsertMasterServiceOfferVariant(variant: MasterServiceOfferVariant): F[QueryFailure, Unit]
  def getMasterServiceOfferVariant(id: MasterServiceOfferVariantId): F[QueryFailure, Option[MasterServiceOfferVariant]]
  def getMasterServiceOfferVariantsByOffer(masterServiceOfferId: MasterServiceOfferId): F[QueryFailure, List[MasterServiceOfferVariant]]
  def getMasterServiceOfferVariantsByLocation(masterLocationId: MasterLocationId): F[QueryFailure, List[MasterServiceOfferVariant]]
}

object MasterServiceOfferVariants {
  /** Model-derived entity metadata for [[MasterServiceOfferVariant]]. */
  val entity: RepoEntity[MasterServiceOfferVariant] = RepoEntity.derived[MasterServiceOfferVariant]

  /** Optional variant by id. */
  def byId[F[_, _]](repo: MasterServiceOfferVariants[F]): OptionalByKey[F, MasterServiceOfferVariantId, MasterServiceOfferVariant] =
    OptionalByKey.derived[F, MasterServiceOfferVariants[F], MasterServiceOfferVariantId, MasterServiceOfferVariant](repo)

  /** Variants by offer id. */
  def byOffer[F[_, _]](repo: MasterServiceOfferVariants[F]): ManyByKey[F, MasterServiceOfferId, MasterServiceOfferVariant] =
    ManyByKey(repo.getMasterServiceOfferVariantsByOffer)

  /** Variants by location id. */
  def byLocation[F[_, _]](repo: MasterServiceOfferVariants[F]): ManyByKey[F, MasterLocationId, MasterServiceOfferVariant] =
    ManyByKey(repo.getMasterServiceOfferVariantsByLocation)

  private case class MasterServiceOfferVariantBaseRow(
    id: MasterServiceOfferVariantId,
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
    priceFrom: BigDecimal,
    priceTo: BigDecimal,
    durationMin: Int,
  )

  private type MasterServiceOfferVariantStoredRow =
    (MasterServiceOfferVariantId, MasterServiceOfferId, MasterLocationId, BigDecimal, BigDecimal, Int, ServiceId)

  private case class StoredVariantData(
    base: MasterServiceOfferVariantBaseRow,
    serviceId: ServiceId,
    attributes: MasterServiceOfferVariantAdditionalAttributes,
  )

  private case class DummyState(
    baseRows: Map[MasterServiceOfferVariantId, MasterServiceOfferVariantBaseRow],
    attributes: MasterServiceOfferVariantAttributesRepository.DummyState,
  )

  private def offerNotFound(masterServiceOfferId: MasterServiceOfferId): QueryFailure =
    QueryFailure.domain(s"MasterServiceOffer $masterServiceOfferId does not exist")

  private def locationNotFound(masterLocationId: MasterLocationId): QueryFailure =
    QueryFailure.domain(s"MasterLocation $masterLocationId does not exist")

  private def offerAndLocationMustBelongToSameMaster(
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
  ): QueryFailure =
    QueryFailure.domain(
      s"MasterServiceOffer $masterServiceOfferId and MasterLocation $masterLocationId must belong to the same master"
    )

  private def attributeNotAllowedForService(
    queryName: String,
    serviceId: ServiceId,
    attributeCode: String,
  ): QueryFailure =
    QueryFailure.operation(queryName, s"Service $serviceId does not allow MasterServiceOfferVariant attribute $attributeCode")

  private def requiredAttributeMissingForService(
    queryName: String,
    serviceId: ServiceId,
    attributeCode: String,
  ): QueryFailure =
    QueryFailure.operation(queryName, s"Service $serviceId requires MasterServiceOfferVariant attribute $attributeCode")

  private def invalidStoredMasterServiceOfferVariant(
    queryName: String,
    message: String,
  ): QueryFailure =
    QueryFailure.operation(queryName, message)

  private def validateAttributesAgainstSchema(
    queryName: String,
    serviceId: ServiceId,
    attributes: MasterServiceOfferVariantAttributes,
    schema: ServiceVariantSchema,
  ): Either[QueryFailure, Unit] =
    schema.validate(attributes).left.map {
      case DisallowedAttribute(attribute) =>
        attributeNotAllowedForService(queryName, serviceId, attribute.code)
      case MissingRequiredAttribute(attribute) =>
        requiredAttributeMissingForService(queryName, serviceId, attribute.code)
    }

  private def liftEither[F[+_, +_]: Error2, A](either: Either[QueryFailure, A]): F[QueryFailure, A] =
    either match {
      case Right(value) =>
        F.pure(value)
      case Left(error) =>
        F.fail(error)
    }

  private def makeVariant[F[+_, +_]: Error2](
    queryName: String,
    stored: StoredVariantData,
    schema: ServiceVariantSchema,
  ): F[QueryFailure, MasterServiceOfferVariant] =
    liftEither[F, MasterServiceOfferVariantAttributes](
      MasterServiceOfferVariantAttributesRepository.decodeStoredAttributes(queryName, stored.attributes)
    ).flatMap {
      attributes =>
        liftEither[F, Unit](validateAttributesAgainstSchema(queryName, stored.serviceId, attributes, schema)).flatMap {
          _ =>
            liftEither[F, MasterServiceOfferVariant](
              MasterServiceOfferVariant
                .make(
                  stored.base.id,
                  stored.base.masterServiceOfferId,
                  stored.base.masterLocationId,
                  stored.base.priceFrom,
                  stored.base.priceTo,
                  stored.base.durationMin,
                  attributes,
                )
                .left
                .map(error => invalidStoredMasterServiceOfferVariant(queryName, error.message))
            )
        }
    }

  private def validateVariantAdditionalAttributes(
    queryName: String,
    serviceId: ServiceId,
    schema: ServiceVariantSchema,
    variant: MasterServiceOfferVariant,
  ): Either[QueryFailure, MasterServiceOfferVariantAdditionalAttributes] = {
    for {
      storedAttributes <- MasterServiceOfferVariantAttributesRepository.encodeStoredAttributes(queryName, variant)
      attributes       <- MasterServiceOfferVariantAttributesRepository.decodeStoredAttributes(queryName, storedAttributes)
      _                <- validateAttributesAgainstSchema(queryName, serviceId, attributes, schema)
    } yield storedAttributes
  }

  private def offerSummary[F[+_, +_]](
    sql: SQL[F]
  )(masterServiceOfferId: MasterServiceOfferId
  ): F[QueryFailure, Option[(MasterId, ServiceId)]] =
    sql.execute("get-master-service-offer-summary") {
      sql"""
        select master_id, service_id
        from master_service_offer
        where id = $masterServiceOfferId
      """.query[(MasterId, ServiceId)].option
    }

  private def locationMasterId[F[+_, +_]](
    sql: SQL[F]
  )(masterLocationId: MasterLocationId
  ): F[QueryFailure, Option[MasterId]] =
    sql.execute("get-master-location-master-id") {
      sql"""
        select master_id
        from master_location
        where id = $masterLocationId
      """.query[MasterId].option
    }

  private def toBaseRow(variant: MasterServiceOfferVariant): MasterServiceOfferVariantBaseRow =
    MasterServiceOfferVariantBaseRow(
      variant.id,
      variant.masterServiceOfferId,
      variant.masterLocationId,
      variant.priceFrom,
      variant.priceTo,
      variant.durationMin,
    )

  private def toStoredVariantData(
    row: MasterServiceOfferVariantStoredRow,
    attributes: MasterServiceOfferVariantAdditionalAttributes,
  ): StoredVariantData =
    StoredVariantData(
      MasterServiceOfferVariantBaseRow(row._1, row._2, row._3, row._4, row._5, row._6),
      row._7,
      attributes,
    )

  private def makeVariantWithSchema[F[+_, +_]: Error2](
    queryName: String,
    stored: StoredVariantData,
    serviceVariantSchemas: ServiceVariantSchemas[F],
  ): F[QueryFailure, MasterServiceOfferVariant] =
    serviceVariantSchemas.getServiceVariantSchema(stored.serviceId).flatMap {
      schema =>
        makeVariant[F](queryName, stored, schema)
    }

  private def loadDummyVariant[F[+_, +_]: Error2](
    queryName: String,
    state: DummyState,
    baseRow: MasterServiceOfferVariantBaseRow,
    masterServiceOffers: MasterServiceOffers[F],
    serviceVariantSchemas: ServiceVariantSchemas[F],
    attributesRepository: MasterServiceOfferVariantAttributesRepository.Dummy,
  ): F[QueryFailure, MasterServiceOfferVariant] =
    masterServiceOffers.getMasterServiceOffer(baseRow.masterServiceOfferId).flatMap {
      case None =>
        F.fail(invalidStoredMasterServiceOfferVariant(queryName, s"MasterServiceOffer ${baseRow.masterServiceOfferId} does not exist"))
      case Some(offer) =>
        makeVariantWithSchema[F](
          queryName,
          StoredVariantData(baseRow, offer.serviceId, attributesRepository.load(state.attributes, baseRow.id)),
          serviceVariantSchemas,
        )
    }

  private def loadDummyVariants[F[+_, +_]: Error2](
    queryName: String,
    state: DummyState,
    rows: List[MasterServiceOfferVariantBaseRow],
    masterServiceOffers: MasterServiceOffers[F],
    serviceVariantSchemas: ServiceVariantSchemas[F],
    attributesRepository: MasterServiceOfferVariantAttributesRepository.Dummy,
  ): F[QueryFailure, List[MasterServiceOfferVariant]] =
    rows.foldRight(F.pure(List.empty[MasterServiceOfferVariant]): F[QueryFailure, List[MasterServiceOfferVariant]]) {
      (row, acc) =>
        loadDummyVariant(queryName, state, row, masterServiceOffers, serviceVariantSchemas, attributesRepository).flatMap {
          value =>
            acc.map(value :: _)
        }
    }

  private def loadStoredVariants(
    rows: List[MasterServiceOfferVariantStoredRow],
    attributesRepository: MasterServiceOfferVariantAttributesRepository.Postgres,
  ): doobie.free.connection.ConnectionIO[Either[QueryFailure, List[StoredVariantData]]] =
    attributesRepository.loadMany(rows.map(_._1)).map {
      attributesById =>
        attributesById.map {
          decodedAttributesById =>
            rows.map {
              row =>
                toStoredVariantData(row, decodedAttributesById.getOrElse(row._1, MasterServiceOfferVariantAdditionalAttributes.empty))
            }
        }
    }

  private def loadStoredVariant(
    row: Option[MasterServiceOfferVariantStoredRow],
    attributesRepository: MasterServiceOfferVariantAttributesRepository.Postgres,
  ): doobie.free.connection.ConnectionIO[Either[QueryFailure, Option[StoredVariantData]]] =
    row match {
      case Some(value) =>
        attributesRepository.load(value._1).map {
          attributes =>
            attributes.map(decoded => Option(toStoredVariantData(value, decoded)))
        }
      case None =>
        FC.pure(Right(Option.empty[StoredVariantData]))
    }

  private def makeStoredVariants[F[+_, +_]: Error2](
    queryName: String,
    rows: List[StoredVariantData],
    serviceVariantSchemas: ServiceVariantSchemas[F],
  ): F[QueryFailure, List[MasterServiceOfferVariant]] =
    rows.foldRight(F.pure(List.empty[MasterServiceOfferVariant]): F[QueryFailure, List[MasterServiceOfferVariant]]) {
      (row, acc) =>
        makeVariantWithSchema(queryName, row, serviceVariantSchemas).flatMap {
          value =>
            acc.map(value :: _)
        }
    }

  class Dummy[F[+_, +_]: Error2: Primitives2](
    masterServiceOffers: MasterServiceOffers[F],
    masterLocations: MasterLocations[F],
    serviceVariantSchemas: ServiceVariantSchemas[F],
  ) extends Lifecycle.LiftF[F[Nothing, _], MasterServiceOfferVariants[F]](
      for {
        state <- F.mkRef(DummyState(Map.empty, MasterServiceOfferVariantAttributesRepository.DummyState.empty))
      } yield {
        val attributesRepository = new MasterServiceOfferVariantAttributesRepository.Dummy

        new MasterServiceOfferVariants[F] {
          def upsertMasterServiceOfferVariant(variant: MasterServiceOfferVariant): F[QueryFailure, Unit] =
            masterServiceOffers.getMasterServiceOffer(variant.masterServiceOfferId).flatMap {
              case None =>
                F.fail(offerNotFound(variant.masterServiceOfferId))
              case Some(offer) =>
                masterLocations.getMasterLocation(variant.masterLocationId).flatMap {
                  case None =>
                    F.fail(locationNotFound(variant.masterLocationId))
                  case Some(location) =>
                    if (offer.masterId != location.masterId) {
                      F.fail(offerAndLocationMustBelongToSameMaster(variant.masterServiceOfferId, variant.masterLocationId))
                    } else {
                      serviceVariantSchemas.getServiceVariantSchema(offer.serviceId).flatMap {
                        schema =>
                          liftEither[F, MasterServiceOfferVariantAdditionalAttributes](
                            validateVariantAdditionalAttributes("upsert-master-service-offer-variant", offer.serviceId, schema, variant)
                          ).flatMap {
                            attributes =>
                              state.update_ {
                                current =>
                                  current.copy(
                                    baseRows   = current.baseRows + (variant.id -> toBaseRow(variant)),
                                    attributes = attributesRepository.replace(variant.id, attributes)(current.attributes),
                                  )
                              }
                          }
                      }
                    }
                }
            }

          def getMasterServiceOfferVariant(id: MasterServiceOfferVariantId): F[QueryFailure, Option[MasterServiceOfferVariant]] =
            state.get.flatMap {
              current =>
                current.baseRows.get(id) match {
                  case Some(baseRow) =>
                    loadDummyVariant(
                      "get-master-service-offer-variant",
                      current,
                      baseRow,
                      masterServiceOffers,
                      serviceVariantSchemas,
                      attributesRepository,
                    ).map(Some(_))
                  case None =>
                    F.pure(None)
                }
            }

          def getMasterServiceOfferVariantsByOffer(masterServiceOfferId: MasterServiceOfferId): F[QueryFailure, List[MasterServiceOfferVariant]] =
            state.get.flatMap {
              current =>
                val rows =
                  current.baseRows.values
                    .filter(_.masterServiceOfferId == masterServiceOfferId)
                    .toList
                    .sortBy(_.id.toString)

                loadDummyVariants(
                  "get-master-service-offer-variants-by-offer",
                  current,
                  rows,
                  masterServiceOffers,
                  serviceVariantSchemas,
                  attributesRepository,
                )
            }

          def getMasterServiceOfferVariantsByLocation(masterLocationId: MasterLocationId): F[QueryFailure, List[MasterServiceOfferVariant]] =
            state.get.flatMap {
              current =>
                val rows =
                  current.baseRows.values
                    .filter(_.masterLocationId == masterLocationId)
                    .toList
                    .sortBy(_.id.toString)

                loadDummyVariants(
                  "get-master-service-offer-variants-by-location",
                  current,
                  rows,
                  masterServiceOffers,
                  serviceVariantSchemas,
                  attributesRepository,
                )
            }
        }
      }
    )

  class Postgres[F[+_, +_]: Error2](
    @unused masterServiceOffers: MasterServiceOffers[F],
    @unused masterLocations: MasterLocations[F],
    serviceVariantSchemas: ServiceVariantSchemas[F],
    sql: SQL[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], MasterServiceOfferVariants[F]](
      for {
        _ <- log.info("Creating MasterServiceOfferVariants table")
        _ <- QueryFailureToThrowable.lift(sql.execute("ddl-master-service-offer-variants") {
          sql"""create table if not exists master_service_offer_variant (
               |  id uuid not null,
               |  master_service_offer_id uuid not null,
               |  master_location_id uuid not null,
               |  price_from numeric not null,
               |  price_to numeric not null,
               |  duration_min int not null,
               |  primary key (id),
               |  constraint master_service_offer_variant_offer_fk
               |    foreign key (master_service_offer_id) references master_service_offer(id),
               |  constraint master_service_offer_variant_location_fk
               |    foreign key (master_location_id) references master_location(id),
               |  constraint master_service_offer_variant_price_from_non_negative
               |    check (price_from >= 0),
               |  constraint master_service_offer_variant_price_range
               |    check (price_to >= price_from),
               |  constraint master_service_offer_variant_duration_positive
               |    check (duration_min > 0)
               |) without oids
               |""".stripMargin.update.run
        })
        _ <- QueryFailureToThrowable.lift(sql.execute("ddl-master-service-offer-variants-offer-id-idx") {
          sql"""
            create index if not exists master_service_offer_variant_offer_id_idx
              on master_service_offer_variant(master_service_offer_id)
          """.update.run
        })
        _ <- QueryFailureToThrowable.lift(sql.execute("ddl-master-service-offer-variants-location-id-idx") {
          sql"""
            create index if not exists master_service_offer_variant_location_id_idx
              on master_service_offer_variant(master_location_id)
          """.update.run
        })
        _ <- QueryFailureToThrowable.lift(sql.execute("ddl-master-service-offer-variant-attributes") {
          new MasterServiceOfferVariantAttributesRepository.Postgres().createTables
        })
      } yield {
        val attributesRepository = new MasterServiceOfferVariantAttributesRepository.Postgres

        new MasterServiceOfferVariants[F] {
          def upsertMasterServiceOfferVariant(variant: MasterServiceOfferVariant): F[QueryFailure, Unit] =
            offerSummary(sql)(variant.masterServiceOfferId).flatMap {
              case None =>
                F.fail(offerNotFound(variant.masterServiceOfferId))
              case Some((offerMasterId, serviceId)) =>
                locationMasterId(sql)(variant.masterLocationId).flatMap {
                  case None =>
                    F.fail(locationNotFound(variant.masterLocationId))
                  case Some(locationOwnerId) =>
                    if (offerMasterId != locationOwnerId) {
                      F.fail(offerAndLocationMustBelongToSameMaster(variant.masterServiceOfferId, variant.masterLocationId))
                    } else {
                      serviceVariantSchemas.getServiceVariantSchema(serviceId).flatMap {
                        schema =>
                          liftEither[F, MasterServiceOfferVariantAdditionalAttributes](
                            validateVariantAdditionalAttributes("upsert-master-service-offer-variant", serviceId, schema, variant)
                          ).flatMap {
                            attributes =>
                              sql
                                .execute("upsert-master-service-offer-variant") {
                                  for {
                                    _ <- sql"""insert into master_service_offer_variant (
                                              |  id,
                                              |  master_service_offer_id,
                                              |  master_location_id,
                                              |  price_from,
                                              |  price_to,
                                              |  duration_min
                                              |)
                                              |values (
                                              |  ${variant.id},
                                              |  ${variant.masterServiceOfferId},
                                              |  ${variant.masterLocationId},
                                              |  ${variant.priceFrom},
                                              |  ${variant.priceTo},
                                              |  ${variant.durationMin}
                                              |)
                                              |on conflict (id) do update set
                                              |  master_service_offer_id = excluded.master_service_offer_id,
                                              |  master_location_id = excluded.master_location_id,
                                              |  price_from = excluded.price_from,
                                              |  price_to = excluded.price_to,
                                              |  duration_min = excluded.duration_min
                                              |""".stripMargin.update.run
                                    _ <- attributesRepository.replace(variant.id, attributes)
                                  } yield ()
                                }
                                .void
                          }
                      }
                    }
                }
            }

          def getMasterServiceOfferVariant(id: MasterServiceOfferVariantId): F[QueryFailure, Option[MasterServiceOfferVariant]] =
            sql
              .execute("get-master-service-offer-variant") {
                for {
                  row <- sql"""select variant.id,
                              |       variant.master_service_offer_id,
                              |       variant.master_location_id,
                              |       variant.price_from,
                              |       variant.price_to,
                              |       variant.duration_min,
                              |       offer.service_id
                              |from master_service_offer_variant variant
                              |join master_service_offer offer on offer.id = variant.master_service_offer_id
                              |where variant.id = $id
                              |""".stripMargin.query[MasterServiceOfferVariantStoredRow].option
                  data <- loadStoredVariant(row, attributesRepository)
                } yield data
              }.flatMap {
                eitherData =>
                  liftEither[F, Option[StoredVariantData]](eitherData).flatMap {
                    case Some(data) =>
                      makeVariantWithSchema[F]("get-master-service-offer-variant", data, serviceVariantSchemas).map(Some(_))
                    case None =>
                      F.pure(None)
                  }
              }

          def getMasterServiceOfferVariantsByOffer(masterServiceOfferId: MasterServiceOfferId): F[QueryFailure, List[MasterServiceOfferVariant]] =
            sql
              .execute("get-master-service-offer-variants-by-offer") {
                for {
                  rows <- sql"""select variant.id,
                               |       variant.master_service_offer_id,
                               |       variant.master_location_id,
                               |       variant.price_from,
                               |       variant.price_to,
                               |       variant.duration_min,
                               |       offer.service_id
                               |from master_service_offer_variant variant
                               |join master_service_offer offer on offer.id = variant.master_service_offer_id
                               |where variant.master_service_offer_id = $masterServiceOfferId
                               |order by variant.id asc
                               |""".stripMargin.query[MasterServiceOfferVariantStoredRow].to[List]
                  data <- loadStoredVariants(rows, attributesRepository)
                } yield data
              }.flatMap {
                eitherData =>
                  liftEither[F, List[StoredVariantData]](eitherData).flatMap(
                    makeStoredVariants[F]("get-master-service-offer-variants-by-offer", _, serviceVariantSchemas)
                  )
              }

          def getMasterServiceOfferVariantsByLocation(masterLocationId: MasterLocationId): F[QueryFailure, List[MasterServiceOfferVariant]] =
            sql
              .execute("get-master-service-offer-variants-by-location") {
                for {
                  rows <- sql"""select variant.id,
                               |       variant.master_service_offer_id,
                               |       variant.master_location_id,
                               |       variant.price_from,
                               |       variant.price_to,
                               |       variant.duration_min,
                               |       offer.service_id
                               |from master_service_offer_variant variant
                               |join master_service_offer offer on offer.id = variant.master_service_offer_id
                               |where variant.master_location_id = $masterLocationId
                               |order by variant.id asc
                               |""".stripMargin.query[MasterServiceOfferVariantStoredRow].to[List]
                  data <- loadStoredVariants(rows, attributesRepository)
                } yield data
              }.flatMap {
                eitherData =>
                  liftEither[F, List[StoredVariantData]](eitherData).flatMap(
                    makeStoredVariants[F]("get-master-service-offer-variants-by-location", _, serviceVariantSchemas)
                  )
              }
        }
      }
    )
}
