package leaderboard.repo

import distage.Lifecycle
import doobie.implicits.*
import doobie.postgres.implicits.*
import izumi.functional.bio.{Error2, F, Primitives2}
import leaderboard.model.{MasterId, MasterServiceOffer, MasterServiceOfferId, QueryFailure, ServiceId}
import leaderboard.sql.SQL
import logstage.LogIO2
import scala.annotation.unused

trait MasterServiceOffers[F[_, _]] {
  def upsertMasterServiceOffer(offer: MasterServiceOffer): F[QueryFailure, Unit]
  def getMasterServiceOffer(id: MasterServiceOfferId): F[QueryFailure, Option[MasterServiceOffer]]
  def getMasterServiceOffersByMaster(masterId: MasterId): F[QueryFailure, List[MasterServiceOffer]]
  def getMasterServiceOffersByService(serviceId: ServiceId): F[QueryFailure, List[MasterServiceOffer]]
}

object MasterServiceOffers {
  private def masterNotFound(masterId: MasterId): QueryFailure =
    QueryFailure("no query", new Exception(s"Master $masterId does not exist"))

  private def serviceNotFound(serviceId: ServiceId): QueryFailure =
    QueryFailure("no query", new Exception(s"Service $serviceId does not exist"))

  private def masterExists[F[+_, +_]](sql: SQL[F])(masterId: MasterId): F[QueryFailure, Boolean] =
    sql.execute("master-exists") {
      sql"""
        select exists(
          select 1
          from masters
          where id = $masterId
        )
      """.query[Boolean].unique
    }

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

  final class Dummy[F[+_, +_]: Error2: Primitives2](
    masters: Masters[F],
    services: Services[F],
  ) extends Lifecycle.LiftF[F[QueryFailure, _], MasterServiceOffers[F]](
      for {
        state <- F.mkRef(Map.empty[MasterServiceOfferId, MasterServiceOffer])
      } yield {
        new MasterServiceOffers[F] {
          def upsertMasterServiceOffer(offer: MasterServiceOffer): F[QueryFailure, Unit] =
            masters.getMaster(offer.masterId).flatMap {
              case None =>
                F.fail(masterNotFound(offer.masterId))
              case Some(_) =>
                services.getService(offer.serviceId).flatMap {
                  case None =>
                    F.fail(serviceNotFound(offer.serviceId))
                  case Some(_) =>
                    state.update_(_ + (offer.id -> offer))
                }
            }

          def getMasterServiceOffer(id: MasterServiceOfferId): F[QueryFailure, Option[MasterServiceOffer]] =
            state.get.map(_.get(id))

          def getMasterServiceOffersByMaster(masterId: MasterId): F[QueryFailure, List[MasterServiceOffer]] =
            state.get.map(
              _.values
                .filter(_.masterId == masterId)
                .toList
                .sortBy(_.id.toString)
            )

          def getMasterServiceOffersByService(serviceId: ServiceId): F[QueryFailure, List[MasterServiceOffer]] =
            state.get.map(
              _.values
                .filter(_.serviceId == serviceId)
                .toList
                .sortBy(_.id.toString)
            )
        }
      }
    )

  final class Postgres[F[+_, +_]: Error2](
    @unused masters: Masters[F],
    @unused services: Services[F],
    sql: SQL[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], MasterServiceOffers[F]](
      for {
        _ <- log.info("Creating MasterServiceOffers table")
        _ <- sql.execute("ddl-master-service-offers") {
          sql"""create table if not exists master_service_offers (
               |  id uuid not null,
               |  master_id uuid not null,
               |  service_id uuid not null,
               |  primary key (id),
               |  constraint master_service_offers_master_fk
               |    foreign key (master_id) references masters(id),
               |  constraint master_service_offers_service_fk
               |    foreign key (service_id) references services(id)
               |) without oids
               |""".stripMargin.update.run
        }
        _ <- sql.execute("ddl-master-service-offers-master-id-idx") {
          sql"""
            create index if not exists master_service_offers_master_id_idx
              on master_service_offers(master_id)
          """.update.run
        }
        _ <- sql.execute("ddl-master-service-offers-service-id-idx") {
          sql"""
            create index if not exists master_service_offers_service_id_idx
              on master_service_offers(service_id)
          """.update.run
        }
      } yield new MasterServiceOffers[F] {
        def upsertMasterServiceOffer(offer: MasterServiceOffer): F[QueryFailure, Unit] =
          masterExists(sql)(offer.masterId).flatMap {
            masterFound =>
              if (!masterFound) {
                F.fail(masterNotFound(offer.masterId))
              } else {
                serviceExists(sql)(offer.serviceId).flatMap {
                  serviceFound =>
                    if (!serviceFound) {
                      F.fail(serviceNotFound(offer.serviceId))
                    } else {
                      sql
                        .execute("upsert-master-service-offer") {
                          sql"""insert into master_service_offers (id, master_id, service_id)
                               |values (${offer.id}, ${offer.masterId}, ${offer.serviceId})
                               |on conflict (id) do update set
                               |  master_id = excluded.master_id,
                               |  service_id = excluded.service_id
                               |""".stripMargin.update.run
                        }
                        .void
                    }
                }
              }
          }

        def getMasterServiceOffer(id: MasterServiceOfferId): F[QueryFailure, Option[MasterServiceOffer]] =
          sql.execute("get-master-service-offer") {
            sql"""select id, master_id, service_id
                 |from master_service_offers
                 |where id = $id
                 |""".stripMargin.query[MasterServiceOffer].option
          }

        def getMasterServiceOffersByMaster(masterId: MasterId): F[QueryFailure, List[MasterServiceOffer]] =
          sql.execute("get-master-service-offers-by-master") {
            sql"""select id, master_id, service_id
                 |from master_service_offers
                 |where master_id = $masterId
                 |order by id asc
                 |""".stripMargin.query[MasterServiceOffer].to[List]
          }

        def getMasterServiceOffersByService(serviceId: ServiceId): F[QueryFailure, List[MasterServiceOffer]] =
          sql.execute("get-master-service-offers-by-service") {
            sql"""select id, master_id, service_id
                 |from master_service_offers
                 |where service_id = $serviceId
                 |order by id asc
                 |""".stripMargin.query[MasterServiceOffer].to[List]
          }
      }
    )
}
