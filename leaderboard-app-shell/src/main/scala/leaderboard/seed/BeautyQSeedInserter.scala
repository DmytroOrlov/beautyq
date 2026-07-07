package leaderboard.seed

import distage.Lifecycle
import izumi.functional.bio.{Error2, F}
import izumi.functional.bio.IO2
import leaderboard.model.QueryFailure
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.runtime.QueryFailureToThrowable
import logstage.LogIO2
import java.util.concurrent.Semaphore

trait BeautyQSeedInserter[F[_, _]] {
  def insert(data: BeautyQSeedData): F[QueryFailure, Unit]
}

object BeautyQSeedInserter {
  private val seedLoadLock = new Semaphore(1, true)

  final class Impl[F[+_, +_]](
    categories: Categories[F],
    services: Services[F],
    masters: Masters[F],
    masterLocations: MasterLocations[F],
    masterServiceOffers: MasterServiceOffers[F],
    serviceVariantSchemas: ServiceVariantSchemas[F],
    masterServiceOfferVariants: MasterServiceOfferVariants[F],
  )(using error2: Error2[F], io: IO2[F]) extends BeautyQSeedInserter[F] {
    def insert(data: BeautyQSeedData): F[QueryFailure, Unit] = {
      io.bracket(io.sync(seedLoadLock.acquireUninterruptibly()))(_ => io.sync(seedLoadLock.release())) { _ =>
        for {
          _ <- insertSequential(data.nonRootCategories.sortBy(category => (category.depth, category.name, category.id.toString)))(categories.upsertCategory)
          _ <- insertSequential(data.services)(services.upsertService)
          _ <- insertSequential(data.masters)(masters.upsertMaster)
          _ <- insertSequential(data.masterLocations)(masterLocations.upsertMasterLocation)
          _ <- insertSequential(data.masterServiceOffers)(masterServiceOffers.upsertMasterServiceOffer)
          _ <- insertSequential(data.serviceVariantSchemas)(serviceVariantSchemas.upsertServiceVariantSchema)
          _ <- insertSequential(data.masterServiceOfferVariants)(masterServiceOfferVariants.upsertMasterServiceOfferVariant)
        } yield ()
      }
    }

    private def insertSequential[A](rows: List[A])(insert: A => F[QueryFailure, Unit]): F[QueryFailure, Unit] =
      rows.foldLeft(error2.pure(()): F[QueryFailure, Unit]) {
        case (acc, row) =>
          error2.flatMap(acc)(_ => insert(row))
      }
  }
}

trait BeautyQSeedReady

object BeautyQSeedReady {
  private object Ready extends BeautyQSeedReady

  final class Noop[F[+_, +_]: Error2]
    extends Lifecycle.LiftF[F[Throwable, _], BeautyQSeedReady](F.pure(Ready))

  final class LoadAndInsert[F[+_, +_]: Error2](
    loader: BeautyQSeedLoader,
    inserter: BeautyQSeedInserter[F],
    log: LogIO2[F],
  ) extends Lifecycle.LiftF[F[Throwable, _], BeautyQSeedReady](
      for {
        seed <- loader.load() match {
          case Right(data) =>
            F.pure(data)
          case Left(error) =>
            F.fail(QueryFailureToThrowable(error))
        }
        _ <- log.info(
          s"""Loading BeautyQ seed: categories=${seed.categories.size}, services=${seed.services.size}, serviceVariantSchemas=${seed.serviceVariantSchemas.size}, masters=${seed.masters.size}, masterLocations=${seed.masterLocations.size}, masterServiceOffers=${seed.masterServiceOffers.size}, masterServiceOfferVariants=${seed.masterServiceOfferVariants.size}"""
        )
        _ <- QueryFailureToThrowable.lift(inserter.insert(seed))
        _ <- log.info("BeautyQ seed loaded")
      } yield Ready
    )
}
