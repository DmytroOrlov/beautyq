package leaderboard.repo

import distage.Lifecycle
import doobie.implicits.*
import doobie.postgres.implicits.*
import izumi.functional.bio.{ApplicativeError2, Error2, F, Primitives2}
import leaderboard.model.{MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariant, MasterServiceOfferVariantId, QueryFailure}
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
  private type MasterServiceOfferVariantRow =
    (MasterServiceOfferVariantId, MasterServiceOfferId, MasterLocationId, BigDecimal, BigDecimal, Int)

  private def offerNotFound(masterServiceOfferId: MasterServiceOfferId): QueryFailure =
    QueryFailure("no query", new Exception(s"MasterServiceOffer $masterServiceOfferId does not exist"))

  private def locationNotFound(masterLocationId: MasterLocationId): QueryFailure =
    QueryFailure("no query", new Exception(s"MasterLocation $masterLocationId does not exist"))

  private def offerAndLocationMustBelongToSameMaster(
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
  ): QueryFailure =
    QueryFailure(
      "no query",
      new Exception(
        s"MasterServiceOffer $masterServiceOfferId and MasterLocation $masterLocationId must belong to the same master"
      ),
    )

  private def invalidStoredMasterServiceOfferVariant(
    queryName: String,
    cause: Throwable,
  ): QueryFailure =
    QueryFailure(queryName, cause)

  private def fromRow[F[+_, +_]: ApplicativeError2](
    queryName: String
  )(row: MasterServiceOfferVariantRow): F[QueryFailure, MasterServiceOfferVariant] =
    MasterServiceOfferVariant.make(row._1, row._2, row._3, row._4, row._5, row._6) match {
      case Right(value) =>
        F.pure(value)
      case Left(error) =>
        F.fail(invalidStoredMasterServiceOfferVariant(queryName, error.asThrowable))
    }

  private def fromRows[F[+_, +_]: Error2](
    queryName: String
  )(rows: List[MasterServiceOfferVariantRow]): F[QueryFailure, List[MasterServiceOfferVariant]] =
    rows.foldRight(F.pure(List.empty[MasterServiceOfferVariant]): F[QueryFailure, List[MasterServiceOfferVariant]]) {
      (row, acc) =>
        fromRow[F](queryName)(row).flatMap {
          value =>
            acc.map(value :: _)
        }
    }

  private def offerExists[F[+_, +_]](sql: SQL[F])(masterServiceOfferId: MasterServiceOfferId): F[QueryFailure, Boolean] =
    sql.execute("master-service-offer-exists") {
      sql"""
        select exists(
          select 1
          from master_service_offers
          where id = $masterServiceOfferId
        )
      """.query[Boolean].unique
    }

  private def locationExists[F[+_, +_]](sql: SQL[F])(masterLocationId: MasterLocationId): F[QueryFailure, Boolean] =
    sql.execute("master-location-exists") {
      sql"""
        select exists(
          select 1
          from master_locations
          where id = $masterLocationId
        )
      """.query[Boolean].unique
    }

  private def offerAndLocationShareMaster[F[+_, +_]](
    sql: SQL[F]
  )(masterServiceOfferId: MasterServiceOfferId, masterLocationId: MasterLocationId): F[QueryFailure, Boolean] =
    sql.execute("master-service-offer-variant-master-match") {
      sql"""
        select exists(
          select 1
          from master_service_offers offer,
               master_locations location
          where offer.id = $masterServiceOfferId
            and location.id = $masterLocationId
            and offer.master_id = location.master_id
        )
      """.query[Boolean].unique
    }

  final class Dummy[F[+_, +_]: Error2: Primitives2](
    masterServiceOffers: MasterServiceOffers[F],
    masterLocations: MasterLocations[F],
  ) extends Lifecycle.LiftF[F[QueryFailure, _], MasterServiceOfferVariants[F]](
      for {
        state <- F.mkRef(Map.empty[MasterServiceOfferVariantId, MasterServiceOfferVariant])
      } yield {
        new MasterServiceOfferVariants[F] {
          override def upsertMasterServiceOfferVariant(variant: MasterServiceOfferVariant): F[QueryFailure, Unit] =
            masterServiceOffers.getMasterServiceOffer(variant.masterServiceOfferId).flatMap {
              case None =>
                F.fail(offerNotFound(variant.masterServiceOfferId))
              case Some(offer) =>
                masterLocations.getMasterLocation(variant.masterLocationId).flatMap {
                  case None =>
                    F.fail(locationNotFound(variant.masterLocationId))
                  case Some(location) =>
                    if (offer.masterId == location.masterId) {
                      state.update_(_ + (variant.id -> variant))
                    } else {
                      F.fail(offerAndLocationMustBelongToSameMaster(variant.masterServiceOfferId, variant.masterLocationId))
                    }
                }
            }

          override def getMasterServiceOfferVariant(id: MasterServiceOfferVariantId): F[QueryFailure, Option[MasterServiceOfferVariant]] =
            state.get.map(_.get(id))

          override def getMasterServiceOfferVariantsByOffer(masterServiceOfferId: MasterServiceOfferId): F[QueryFailure, List[MasterServiceOfferVariant]] =
            state.get.map(
              _.values
                .filter(_.masterServiceOfferId == masterServiceOfferId)
                .toList
                .sortBy(_.id.toString)
            )

          override def getMasterServiceOfferVariantsByLocation(masterLocationId: MasterLocationId): F[QueryFailure, List[MasterServiceOfferVariant]] =
            state.get.map(
              _.values
                .filter(_.masterLocationId == masterLocationId)
                .toList
                .sortBy(_.id.toString)
            )
        }
      }
    )

  final class Postgres[F[+_, +_]: Error2](
    @unused masterServiceOffers: MasterServiceOffers[F],
    @unused masterLocations: MasterLocations[F],
    sql: SQL[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], MasterServiceOfferVariants[F]](
      for {
        _ <- log.info("Creating MasterServiceOfferVariants table")
        _ <- sql.execute("ddl-master-service-offer-variants") {
          sql"""create table if not exists master_service_offer_variants (
               |  id uuid not null,
               |  master_service_offer_id uuid not null,
               |  master_location_id uuid not null,
               |  price_from numeric not null,
               |  price_to numeric not null,
               |  duration_min int not null,
               |  primary key (id),
               |  constraint master_service_offer_variants_offer_fk
               |    foreign key (master_service_offer_id) references master_service_offers(id),
               |  constraint master_service_offer_variants_location_fk
               |    foreign key (master_location_id) references master_locations(id),
               |  constraint master_service_offer_variants_price_from_non_negative
               |    check (price_from >= 0),
               |  constraint master_service_offer_variants_price_range
               |    check (price_to >= price_from),
               |  constraint master_service_offer_variants_duration_positive
               |    check (duration_min > 0)
               |) without oids
               |""".stripMargin.update.run
        }
        _ <- sql.execute("ddl-master-service-offer-variants-offer-id-idx") {
          sql"""
            create index if not exists master_service_offer_variants_offer_id_idx
              on master_service_offer_variants(master_service_offer_id)
          """.update.run
        }
        _ <- sql.execute("ddl-master-service-offer-variants-location-id-idx") {
          sql"""
            create index if not exists master_service_offer_variants_location_id_idx
              on master_service_offer_variants(master_location_id)
          """.update.run
        }
      } yield new MasterServiceOfferVariants[F] {
        override def upsertMasterServiceOfferVariant(variant: MasterServiceOfferVariant): F[QueryFailure, Unit] =
          offerExists(sql)(variant.masterServiceOfferId).flatMap {
            offerFound =>
              if (!offerFound) {
                F.fail(offerNotFound(variant.masterServiceOfferId))
              } else {
                locationExists(sql)(variant.masterLocationId).flatMap {
                  locationFound =>
                    if (!locationFound) {
                      F.fail(locationNotFound(variant.masterLocationId))
                    } else {
                      offerAndLocationShareMaster(sql)(variant.masterServiceOfferId, variant.masterLocationId).flatMap {
                        sameMaster =>
                          if (!sameMaster) {
                            F.fail(offerAndLocationMustBelongToSameMaster(variant.masterServiceOfferId, variant.masterLocationId))
                          } else {
                            sql
                              .execute("upsert-master-service-offer-variant") {
                                sql"""insert into master_service_offer_variants (
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
                              }
                              .void
                          }
                      }
                    }
                }
              }
          }

        override def getMasterServiceOfferVariant(id: MasterServiceOfferVariantId): F[QueryFailure, Option[MasterServiceOfferVariant]] =
          sql.execute("get-master-service-offer-variant") {
            sql"""select id, master_service_offer_id, master_location_id, price_from, price_to, duration_min
                 |from master_service_offer_variants
                 |where id = $id
                 |""".stripMargin.query[MasterServiceOfferVariantRow].option
          }.flatMap {
            case Some(row) =>
              fromRow[F]("get-master-service-offer-variant")(row).map(Some(_))
            case None =>
              F.pure(None)
          }

        override def getMasterServiceOfferVariantsByOffer(masterServiceOfferId: MasterServiceOfferId): F[QueryFailure, List[MasterServiceOfferVariant]] =
          sql.execute("get-master-service-offer-variants-by-offer") {
            sql"""select id, master_service_offer_id, master_location_id, price_from, price_to, duration_min
                 |from master_service_offer_variants
                 |where master_service_offer_id = $masterServiceOfferId
                 |order by id asc
                 |""".stripMargin.query[MasterServiceOfferVariantRow].to[List]
          }.flatMap(fromRows[F]("get-master-service-offer-variants-by-offer"))

        override def getMasterServiceOfferVariantsByLocation(masterLocationId: MasterLocationId): F[QueryFailure, List[MasterServiceOfferVariant]] =
          sql.execute("get-master-service-offer-variants-by-location") {
            sql"""select id, master_service_offer_id, master_location_id, price_from, price_to, duration_min
                 |from master_service_offer_variants
                 |where master_location_id = $masterLocationId
                 |order by id asc
                 |""".stripMargin.query[MasterServiceOfferVariantRow].to[List]
          }.flatMap(fromRows[F]("get-master-service-offer-variants-by-location"))
      }
    )
}
