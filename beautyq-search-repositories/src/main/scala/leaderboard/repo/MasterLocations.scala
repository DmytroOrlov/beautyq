package leaderboard.repo

import distage.Lifecycle
import doobie.implicits.*
import doobie.postgres.implicits.*
import izumi.functional.bio.{Error2, F, Primitives2}
import leaderboard.model.{MasterId, MasterLocation, MasterLocationId, QueryFailure}
import leaderboard.repo.RepoOp.{ManyByKey, OptionalByKey}
import leaderboard.runtime.QueryFailureToThrowable
import leaderboard.sql.SQL
import logstage.LogIO2
import scala.annotation.unused

trait MasterLocations[F[_, _]] {
  def upsertMasterLocation(location: MasterLocation): F[QueryFailure, Unit]
  def getMasterLocation(id: MasterLocationId): F[QueryFailure, Option[MasterLocation]]
  def getMasterLocationsByMaster(masterId: MasterId): F[QueryFailure, List[MasterLocation]]
}

object MasterLocations {
  /** Model-derived entity metadata for [[MasterLocation]]. */
  val entity: RepoEntity[MasterLocation] = RepoEntity.derived[MasterLocation]

  /** Optional master location by id. */
  def byId[F[_, _]](repo: MasterLocations[F]): OptionalByKey[F, MasterLocationId, MasterLocation] =
    OptionalByKey(repo.getMasterLocation)

  /** Locations by master id. */
  def byMaster[F[_, _]](repo: MasterLocations[F]): ManyByKey[F, MasterId, MasterLocation] =
    ManyByKey(repo.getMasterLocationsByMaster)

  private def masterNotFound(masterId: MasterId): QueryFailure =
    QueryFailure.domain(s"Master $masterId does not exist")

  private def masterExists[F[+_, +_]](sql: SQL[F])(masterId: MasterId): F[QueryFailure, Boolean] =
    sql.execute("master-exists") {
      sql"""
        select exists(
          select 1
          from master
          where id = $masterId
        )
      """.query[Boolean].unique
    }

  class Dummy[F[+_, +_]: Error2: Primitives2](
    masters: Masters[F]
  ) extends Lifecycle.LiftF[F[Nothing, _], MasterLocations[F]](
      for {
        state <- F.mkRef(Map.empty[MasterLocationId, MasterLocation])
      } yield {
        new MasterLocations[F] {
          def upsertMasterLocation(location: MasterLocation): F[QueryFailure, Unit] =
            masters.getMaster(location.masterId).flatMap {
              case Some(_) =>
                state.update_(_ + (location.id -> location))
              case None =>
                F.fail(masterNotFound(location.masterId))
            }

          def getMasterLocation(id: MasterLocationId): F[QueryFailure, Option[MasterLocation]] =
            state.get.map(_.get(id))

          def getMasterLocationsByMaster(masterId: MasterId): F[QueryFailure, List[MasterLocation]] =
            state.get.map(
              _.values
                .filter(_.masterId == masterId)
                .toList
                .sortBy(location => (location.name, location.id.toString))
            )
        }
      }
    )

  class Postgres[F[+_, +_]: Error2](
    @unused masters: Masters[F],
    sql: SQL[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], MasterLocations[F]](
      for {
        _ <- log.info("Creating MasterLocations table")
        _ <- QueryFailureToThrowable.lift(sql.execute("ddl-master-locations") {
          sql"""create table if not exists master_location (
               |  id uuid not null,
               |  master_id uuid not null,
               |  name text not null,
               |  address text not null,
               |  lat numeric not null,
               |  lon numeric not null,
               |  primary key (id),
               |  constraint master_location_master_fk
               |    foreign key (master_id) references master(id)
               |) without oids
               |""".stripMargin.update.run
        })
        _ <- QueryFailureToThrowable.lift(sql.execute("ddl-master-locations-master-id-idx") {
          sql"""
            create index if not exists master_location_master_id_idx
              on master_location(master_id)
          """.update.run
        })
      } yield new MasterLocations[F] {

        def upsertMasterLocation(location: MasterLocation): F[QueryFailure, Unit] =
          masterExists(sql)(location.masterId).flatMap {
            exists =>
              if (!exists) {
                F.fail(masterNotFound(location.masterId))
              } else {
                sql
                  .execute("upsert-master-location") {
                    sql"""insert into master_location (id, master_id, name, address, lat, lon)
                         |values (
                         |  ${location.id},
                         |  ${location.masterId},
                         |  ${location.name},
                         |  ${location.address},
                         |  ${location.lat},
                         |  ${location.lon}
                         |)
                         |on conflict (id) do update set
                         |  master_id = excluded.master_id,
                         |  name = excluded.name,
                         |  address = excluded.address,
                         |  lat = excluded.lat,
                         |  lon = excluded.lon
                         |""".stripMargin.update.run
                  }
                  .void
              }
          }

        def getMasterLocation(id: MasterLocationId): F[QueryFailure, Option[MasterLocation]] =
          sql.execute("get-master-location") {
            sql"""select id, master_id, name, address, lat, lon
                 |from master_location
                 |where id = $id
                 |""".stripMargin.query[MasterLocation].option
          }

        def getMasterLocationsByMaster(masterId: MasterId): F[QueryFailure, List[MasterLocation]] =
          sql.execute("get-master-locations-by-master") {
            sql"""select id, master_id, name, address, lat, lon
                 |from master_location
                 |where master_id = $masterId
                 |order by name asc, id asc
                 |""".stripMargin.query[MasterLocation].to[List]
          }
      }
    )
}
