package leaderboard.search.gen2

import doobie.free.{connection => FC}
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import leaderboard.{LeaderboardTest, ProdTest, VariantTestFixtures}
import leaderboard.model.*
import leaderboard.model.Category.CategoryId
import leaderboard.repo.*
import leaderboard.repo.given
import leaderboard.search.beautyq.gen2.materialization.*
import leaderboard.seed.{BeautyQSeedData, BeautyQSeedInserter, BeautyQSeedLoader}
import leaderboard.sql.SQL
import zio.{IO, ZIO}

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID
import java.util.concurrent.CountDownLatch

/** Brick 3 PostgreSQL integration proof: the snapshot source's transaction mode, a full round-trip over
  * the current seed, and a deterministic proof that a paused snapshot read is isolated from a concurrent
  * writer transaction under repeatable-read semantics.
  */
final class BeautyQGen2SnapshotPostgresSpec extends LeaderboardTest with ProdTest with VariantTestFixtures {
  private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

  private def dieOnLeft[E, A](either: Either[E, A])(messageOf: E => String): IO[Nothing, A] =
    either match {
      case Right(value) => ZIO.succeed(value)
      case Left(error)  => ZIO.die(new RuntimeException(messageOf(error)))
    }

  private def dieOnFailure[E, A](io: IO[E, A])(messageOf: E => String): IO[Nothing, A] =
    io.foldZIO(error => ZIO.die(new RuntimeException(messageOf(error))), ZIO.succeed(_))

  private def missingSeedVariantIds(
    seed: BeautyQSeedData,
    variants: MasterServiceOfferVariants[IO],
  ): IO[QueryFailure, List[MasterServiceOfferVariantId]] =
    ZIO
      .foreach(seed.masterServiceOfferVariants) {
        expected =>
          variants.getMasterServiceOfferVariant(expected.id).map {
            case Some(_) => None
            case None    => Some(expected.id)
          }
      }
      .map(_.collect { case Some(id) => id })

  // Mirrors the idempotent check-then-insert-then-recheck pattern already used by BeautyQSeedSpec: never
  // assumes the shared integration database starts empty, and inserts the seed only when it is missing.
  private def ensureSeedLoaded(
    seed: BeautyQSeedData,
    inserter: BeautyQSeedInserter[IO],
    variants: MasterServiceOfferVariants[IO],
  ): IO[QueryFailure, Unit] =
    missingSeedVariantIds(seed, variants).flatMap {
      case Nil =>
        ZIO.unit
      case _ =>
        inserter.insert(seed).either.flatMap {
          case Right(_) =>
            missingSeedVariantIds(seed, variants).flatMap {
              case Nil => ZIO.unit
              case remaining =>
                ZIO.fail(QueryFailure.domain(s"Seed did not load all current variants; missing: ${remaining.map(_.toString).sorted.mkString(", ")}"))
            }
          case Left(error) =>
            missingSeedVariantIds(seed, variants).flatMap {
              case Nil => ZIO.unit
              case _   => ZIO.fail(error)
            }
        }
    }

  private val tornCategoryId = CategoryId(UUID.fromString("f0000000-0000-0000-0000-000000000001"))
  private val tornServiceId = ServiceId(UUID.fromString("f0000000-0000-0000-0000-000000000002"))
  private val tornMasterId = MasterId(UUID.fromString("f0000000-0000-0000-0000-000000000003"))
  private val tornLocationId = MasterLocationId(UUID.fromString("f0000000-0000-0000-0000-000000000004"))
  private val tornOfferId = MasterServiceOfferId(UUID.fromString("f0000000-0000-0000-0000-000000000005"))
  private val tornVariantId = MasterServiceOfferVariantId(UUID.fromString("f0000000-0000-0000-0000-000000000006"))

  "BeautyQSearchSnapshotSource.Postgres" should {
    "run its transaction in repeatable-read, read-only mode" in {
      (sql: SQL[IO]) =>
        for {
          modes <- dieOnFailure(
            sql.readOnlyRepeatableRead("test-transaction-mode") {
              for {
                isolation <- sql"show transaction_isolation".query[String].unique
                readOnly <- sql"show transaction_read_only".query[String].unique
              } yield (isolation, readOnly)
            }
          )(error => s"failed to read transaction mode: ${error.message}")
          _ <- assertIO(modes == ("repeatable read" -> "on"))
        } yield ()
    }

    "load the current seed snapshot deterministically and project exactly the 66 seed documents" in {
      (
        inserter: BeautyQSeedInserter[IO],
        variants: MasterServiceOfferVariants[IO],
        sql: SQL[IO],
      ) =>
        val seedLoader = new BeautyQSeedLoader.ResourceLoader()
        val snapshotSource = new BeautyQSearchSnapshotSource.Postgres[IO](sql, fixedClock)

        for {
          seed <- dieOnLeft(seedLoader.load())(error => s"failed to load the BeautyQ seed resource: ${error.message}")
          _ <- dieOnFailure(ensureSeedLoaded(seed, inserter, variants))(error => s"failed to ensure the BeautyQ seed is loaded: ${error.message}")

          // The integration database is shared with every other repository test in a full-suite run, so
          // it may legitimately contain more than the seed's own rows by the time this test runs. Scope
          // every assertion to the seed's own variant IDs rather than assuming the database contains
          // exactly (and only) the seed - the whole-database-size and cross-load-equality contracts
          // belong to the pure fingerprint/materializer suites, where the input is truly immutable.
          expectedSeedVariantIds = seed.masterServiceOfferVariants.map(_.id).toSet
          _ <- assertIO(expectedSeedVariantIds.size == 66)

          versioned <- dieOnFailure(snapshotSource.load)(error => s"failed to load the current snapshot: ${error.message}")
          documents <- dieOnLeft(BeautyQVariantProjectionGen2.project(versioned.value)) {
            errors => s"current snapshot unexpectedly failed projection: ${errors.toVector.map(_.message).mkString("; ")}"
          }

          snapshotVariantIds = versioned.value.masterServiceOfferVariants.map(_.id).toSet
          seedDocuments = documents.filter(document => expectedSeedVariantIds.contains(document.variantId))
          seedDocumentIds = seedDocuments.map(_.variantId)

          _ <- assertIO(expectedSeedVariantIds.subsetOf(snapshotVariantIds))
          _ <- assertIO(seedDocuments.size == 66)
          _ <- assertIO(seedDocumentIds.toSet == expectedSeedVariantIds)
          _ <- assertIO(seedDocumentIds.distinct.size == 66)
          _ <- assertIO(seedDocumentIds.map(_.toString) == seedDocumentIds.map(_.toString).sorted)
          _ <- assertIO(seedDocuments.exists(_.categoryCode == CategoryCode.unsafeFromString("nails")))
          _ <- assertIO(seedDocuments.exists(_.serviceCode == ServiceCode.unsafeFromString("manicure")))
        } yield ()
    }

    "isolate a paused snapshot read from a concurrent writer transaction (torn-snapshot proof)" in {
      (
        categories: Categories[IO],
        services: Services[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        masterServiceOffers: MasterServiceOffers[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterServiceOfferVariants: MasterServiceOfferVariants[IO],
        sql: SQL[IO],
      ) =>
        val category = Category(tornCategoryId, testCategoryCode(tornCategoryId), Category.rootCategoryId, 1, "Torn Snapshot Category")
        val service = Service(tornServiceId, testServiceCode(tornServiceId), tornCategoryId, "Snapshot Old Service")
        val master = Master(tornMasterId, "Torn Snapshot Master")
        val location = MasterLocation(tornLocationId, tornMasterId, "Torn Snapshot Location", "1 Torn Street", BigDecimal("1.000000"), BigDecimal("2.000000"))
        val offer = MasterServiceOffer(tornOfferId, tornMasterId, tornServiceId)

        for {
          _ <- dieOnFailure(categories.upsertCategory(category))(error => s"setup: upsert category failed: ${error.message}")
          _ <- dieOnFailure(services.upsertService(service))(error => s"setup: upsert service failed: ${error.message}")
          _ <- dieOnFailure(serviceVariantSchemas.upsertServiceVariantSchema(makeSchema(tornServiceId)))(error => s"setup: upsert schema failed: ${error.message}")
          _ <- dieOnFailure(masters.upsertMaster(master))(error => s"setup: upsert master failed: ${error.message}")
          _ <- dieOnFailure(masterLocations.upsertMasterLocation(location))(error => s"setup: upsert location failed: ${error.message}")
          _ <- dieOnFailure(masterServiceOffers.upsertMasterServiceOffer(offer))(error => s"setup: upsert offer failed: ${error.message}")
          variant <- dieOnFailure(
            makeVariant(tornVariantId, tornOfferId, tornLocationId, priceFrom = BigDecimal(10), priceTo = BigDecimal(20), durationMin = 30)
          )(error => s"setup: build variant failed: ${error.message}")
          _ <- dieOnFailure(masterServiceOfferVariants.upsertMasterServiceOfferVariant(variant))(error => s"setup: upsert variant failed: ${error.message}")

          snapshotPaused = new CountDownLatch(1)
          allowSnapshotToContinue = new CountDownLatch(1)
          pausingHook = new SnapshotReadHook {
            def afterServicesAndSchemas: ConnectionIO[Unit] =
              FC.delay {
                snapshotPaused.countDown()
                allowSnapshotToContinue.await()
              }
          }
          pausingSnapshotSource = new BeautyQSearchSnapshotSource.Postgres[IO](sql, fixedClock, pausingHook)

          fiber <- pausingSnapshotSource.load.fork
          _ <- ZIO.attemptBlocking(snapshotPaused.await()).orDie
          _ <- sql
            .execute("test-torn-snapshot-writer") {
              for {
                _ <- sql"update service set name = ${"Snapshot New Service"} where id = $tornServiceId".update.run
                _ <- sql"update master_service_offer_variant set price_from = ${BigDecimal(30)}, price_to = ${BigDecimal(40)} where id = $tornVariantId".update.run
              } yield ()
            }
            .foldZIO(error => ZIO.die(new RuntimeException(s"writer transaction failed: ${error.message}")), ZIO.succeed(_))
          _ <- ZIO.succeed(allowSnapshotToContinue.countDown())
          pausedVersioned <- fiber.join.foldZIO(error => ZIO.die(new RuntimeException(s"paused snapshot load failed: ${error.message}")), ZIO.succeed(_))

          freshSnapshotSource = new BeautyQSearchSnapshotSource.Postgres[IO](sql, fixedClock)
          freshVersioned <- dieOnFailure(freshSnapshotSource.load)(error => s"failed to load the post-writer snapshot: ${error.message}")

          // Locate the test-owned rows by their exact fixed IDs inside the larger shared snapshot - never
          // assume the snapshot contains only seed and torn-snapshot rows.
          _ <- assertIO(pausedVersioned.value.services.exists(s => s.id == tornServiceId && s.name == "Snapshot Old Service"))
          _ <- assertIO(
            pausedVersioned.value.masterServiceOfferVariants.exists {
              v => v.id == tornVariantId && v.priceFrom.compare(BigDecimal(10)) == 0 && v.priceTo.compare(BigDecimal(20)) == 0
            }
          )
          // Neither torn combination (old service + new prices, or new service + old prices) is visible.
          _ <- assertIO(!pausedVersioned.value.services.exists(s => s.id == tornServiceId && s.name == "Snapshot New Service"))
          _ <- assertIO(
            !pausedVersioned.value.masterServiceOfferVariants.exists {
              v => v.id == tornVariantId && v.priceFrom.compare(BigDecimal(30)) == 0 && v.priceTo.compare(BigDecimal(40)) == 0
            }
          )
          _ <- assertIO(freshVersioned.value.services.exists(s => s.id == tornServiceId && s.name == "Snapshot New Service"))
          _ <- assertIO(
            freshVersioned.value.masterServiceOfferVariants.exists {
              v => v.id == tornVariantId && v.priceFrom.compare(BigDecimal(30)) == 0 && v.priceTo.compare(BigDecimal(40)) == 0
            }
          )
          _ <- assertIO(pausedVersioned.contentFingerprint != freshVersioned.contentFingerprint)
        } yield ()
    }
  }
}
