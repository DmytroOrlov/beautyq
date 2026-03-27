package leaderboard.repo

import distage.Lifecycle
import doobie.implicits.*
import doobie.postgres.implicits.*
import izumi.functional.bio.{Error2, F, Primitives2}
import leaderboard.model.{MasterLocationId, MasterServiceOfferId, MasterServiceOfferLocation, MasterServiceOfferLocationId, QueryFailure}
import leaderboard.sql.SQL
import logstage.LogIO2

trait MasterServiceOfferLocations[F[_, _]] {
  def upsertMasterServiceOfferLocation(link: MasterServiceOfferLocation): F[QueryFailure, Unit]
  def getMasterServiceOfferLocation(id: MasterServiceOfferLocationId): F[QueryFailure, Option[MasterServiceOfferLocation]]
  def getMasterServiceOfferLocationsByOffer(masterServiceOfferId: MasterServiceOfferId): F[QueryFailure, List[MasterServiceOfferLocation]]
  def getMasterServiceOfferLocationsByLocation(masterLocationId: MasterLocationId): F[QueryFailure, List[MasterServiceOfferLocation]]
}

object MasterServiceOfferLocations {
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
    sql.execute("master-service-offer-location-master-match") {
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
  ) extends Lifecycle.LiftF[F[QueryFailure, _], MasterServiceOfferLocations[F]](
      for {
        state <- F.mkRef(Map.empty[MasterServiceOfferLocationId, MasterServiceOfferLocation])
      } yield {
        new MasterServiceOfferLocations[F] {
          override def upsertMasterServiceOfferLocation(link: MasterServiceOfferLocation): F[QueryFailure, Unit] =
            masterServiceOffers.getMasterServiceOffer(link.masterServiceOfferId).flatMap {
              case None =>
                F.fail(offerNotFound(link.masterServiceOfferId))
              case Some(linkOffer) =>
                masterLocations.getMasterLocation(link.masterLocationId).flatMap {
                  case None =>
                    F.fail(locationNotFound(link.masterLocationId))
                  case Some(location) =>
                    if (linkOffer.masterId == location.masterId) {
                      state.update_(_ + (link.id -> link))
                    } else {
                      F.fail(offerAndLocationMustBelongToSameMaster(link.masterServiceOfferId, link.masterLocationId))
                    }
                }
            }

          override def getMasterServiceOfferLocation(id: MasterServiceOfferLocationId): F[QueryFailure, Option[MasterServiceOfferLocation]] =
            state.get.map(_.get(id))

          override def getMasterServiceOfferLocationsByOffer(masterServiceOfferId: MasterServiceOfferId): F[QueryFailure, List[MasterServiceOfferLocation]] =
            state.get.map(
              _.values
                .filter(_.masterServiceOfferId == masterServiceOfferId)
                .toList
                .sortBy(_.id.toString)
            )

          override def getMasterServiceOfferLocationsByLocation(masterLocationId: MasterLocationId): F[QueryFailure, List[MasterServiceOfferLocation]] =
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
    sql: SQL[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], MasterServiceOfferLocations[F]](
      for {
        _ <- log.info("Creating MasterServiceOfferLocations table")
        _ <- sql.execute("ddl-master-service-offer-locations") {
          sql"""create table if not exists master_service_offer_locations (
               |  id uuid not null,
               |  master_service_offer_id uuid not null,
               |  master_location_id uuid not null,
               |  primary key (id),
               |  constraint master_service_offer_locations_offer_fk
               |    foreign key (master_service_offer_id) references master_service_offers(id),
               |  constraint master_service_offer_locations_location_fk
               |    foreign key (master_location_id) references master_locations(id)
               |) without oids
               |""".stripMargin.update.run
        }
        _ <- sql.execute("ddl-master-service-offer-locations-offer-id-idx") {
          sql"""
            create index if not exists master_service_offer_locations_offer_id_idx
              on master_service_offer_locations(master_service_offer_id)
          """.update.run
        }
        _ <- sql.execute("ddl-master-service-offer-locations-location-id-idx") {
          sql"""
            create index if not exists master_service_offer_locations_location_id_idx
              on master_service_offer_locations(master_location_id)
          """.update.run
        }
      } yield new MasterServiceOfferLocations[F] {
        override def upsertMasterServiceOfferLocation(link: MasterServiceOfferLocation): F[QueryFailure, Unit] =
          offerExists(sql)(link.masterServiceOfferId).flatMap {
            offerFound =>
              if (!offerFound) {
                F.fail(offerNotFound(link.masterServiceOfferId))
              } else {
                locationExists(sql)(link.masterLocationId).flatMap {
                  locationFound =>
                    if (!locationFound) {
                      F.fail(locationNotFound(link.masterLocationId))
                    } else {
                      offerAndLocationShareMaster(sql)(link.masterServiceOfferId, link.masterLocationId).flatMap {
                        sameMaster =>
                          if (!sameMaster) {
                            F.fail(offerAndLocationMustBelongToSameMaster(link.masterServiceOfferId, link.masterLocationId))
                          } else {
                            sql
                              .execute("upsert-master-service-offer-location") {
                                sql"""insert into master_service_offer_locations (id, master_service_offer_id, master_location_id)
                                     |values (${link.id}, ${link.masterServiceOfferId}, ${link.masterLocationId})
                                     |on conflict (id) do update set
                                     |  master_service_offer_id = excluded.master_service_offer_id,
                                     |  master_location_id = excluded.master_location_id
                                     |""".stripMargin.update.run
                              }
                              .void
                          }
                      }
                    }
                }
              }
          }

        override def getMasterServiceOfferLocation(id: MasterServiceOfferLocationId): F[QueryFailure, Option[MasterServiceOfferLocation]] =
          sql.execute("get-master-service-offer-location") {
            sql"""select id, master_service_offer_id, master_location_id
                 |from master_service_offer_locations
                 |where id = $id
                 |""".stripMargin.query[MasterServiceOfferLocation].option
          }

        override def getMasterServiceOfferLocationsByOffer(masterServiceOfferId: MasterServiceOfferId): F[QueryFailure, List[MasterServiceOfferLocation]] =
          sql.execute("get-master-service-offer-locations-by-offer") {
            sql"""select id, master_service_offer_id, master_location_id
                 |from master_service_offer_locations
                 |where master_service_offer_id = $masterServiceOfferId
                 |order by id asc
                 |""".stripMargin.query[MasterServiceOfferLocation].to[List]
          }

        override def getMasterServiceOfferLocationsByLocation(masterLocationId: MasterLocationId): F[QueryFailure, List[MasterServiceOfferLocation]] =
          sql.execute("get-master-service-offer-locations-by-location") {
            sql"""select id, master_service_offer_id, master_location_id
                 |from master_service_offer_locations
                 |where master_location_id = $masterLocationId
                 |order by id asc
                 |""".stripMargin.query[MasterServiceOfferLocation].to[List]
          }
      }
    )
}
