package leaderboard.seed

import io.circe.Json
import io.circe.parser.parse
import izumi.distage.model.definition.Activation
import distage.Mode
import leaderboard.{DummyTest, LeaderboardTest, ProdTest}
import leaderboard.model.AttributeDefinition
import leaderboard.model.*
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import zio.{IO, ZIO}

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.util.Using

abstract class BeautyQSeedSpec extends LeaderboardTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test)
  )

  private val seedLoader = new BeautyQSeedLoader.ResourceLoader()

  private val knownCategoryId = UUID.fromString("e1558eb7-8f7d-5b71-844d-71cc37a192e8")
  private val knownServiceId = UUID.fromString("a1085253-a9bf-517c-80c4-262b0bf9a5a4")
  private val knownMasterId = UUID.fromString("6bbb7472-d382-541e-a007-c842ebf3c25b")
  private val knownLocationId = UUID.fromString("78fdf5d2-0f92-5c2c-b20d-e5d2549d1c52")
  private val knownOfferId = UUID.fromString("73a9fc17-4f0d-5417-b04d-a65f8fdb2399")
  private val knownVariantId = UUID.fromString("1fcd6e17-c6bb-5901-9f63-205668897659")

  private def loadSkippedRecordsFromResourceOrDie: IO[Nothing, List[Json]] = {
    val result = for {
      input <- Option(getClass.getClassLoader.getResourceAsStream(BeautyQSeedLoader.DefaultResourcePath))
        .toRight(s"Resource ${BeautyQSeedLoader.DefaultResourcePath} not found")
      content <- Using(input)(stream => new String(stream.readAllBytes(), StandardCharsets.UTF_8)).toEither.left.map(_.getMessage)
      json <- parse(content).left.map(_.getMessage)
      skipped <- json.hcursor.get[List[Json]]("skippedRecords").left.map(_.getMessage)
    } yield skipped

    result match {
      case Right(value) =>
        ZIO.succeed(value)
      case Left(error) =>
        ZIO.die(new RuntimeException(error))
    }
  }

  private def loadSeedOrDie: IO[Nothing, BeautyQSeedData] =
    seedLoader.load() match {
      case Right(seed) =>
        ZIO.succeed(seed)
      case Left(error) =>
        ZIO.die(new RuntimeException(error.message))
    }

  private def fetchLoadedVariants(
    seed: BeautyQSeedData,
    variants: MasterServiceOfferVariants[IO],
  ): IO[QueryFailure, List[MasterServiceOfferVariant]] =
    ZIO.foreach(seed.masterServiceOfferVariants) {
      expected =>
        variants.getMasterServiceOfferVariant(expected.id).flatMap {
          case Some(variant) =>
            ZIO.succeed(variant)
          case None =>
            ZIO.fail(QueryFailure.domain(s"Seed variant ${expected.id} was not found after load"))
        }
    }

  private def missingSeedVariantIds(
    seed: BeautyQSeedData,
    variants: MasterServiceOfferVariants[IO],
  ): IO[QueryFailure, List[MasterServiceOfferVariantId]] =
    ZIO
      .foreach(seed.masterServiceOfferVariants) {
        expected =>
          variants.getMasterServiceOfferVariant(expected.id).map {
            case Some(_) =>
              None
            case None =>
              Some(expected.id)
          }
      }
      .map(_.collect { case Some(id) => id })

  private def failIfSeedVariantsMissing(
    missingIds: List[MasterServiceOfferVariantId]
  ): IO[QueryFailure, Unit] =
    missingIds match {
      case Nil =>
        ZIO.unit
      case ids =>
        ZIO.fail(
          QueryFailure.domain(
            s"Seed did not load all current variants; missing variant id(s): ${ids.map(_.toString).sorted.mkString(", ")}"
          )
        )
    }

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
            missingSeedVariantIds(seed, variants).flatMap(failIfSeedVariantsMissing)
          case Left(error) =>
            missingSeedVariantIds(seed, variants).flatMap {
              case Nil =>
                ZIO.unit
              case _ =>
                ZIO.fail(error)
            }
        }
    }

  "BeautyQ seed loader" should {
    "decode seed JSON from resource" in {
      for {
        seed <- loadSeedOrDie
        skipped <- loadSkippedRecordsFromResourceOrDie
        _ <- assertIO(seed.categories.size == 5)
        _ <- assertIO(seed.services.size == 9)
        _ <- assertIO(seed.serviceVariantSchemas.size == 9)
        _ <- assertIO(seed.masters.size == 8)
        _ <- assertIO(seed.masterLocations.size == 8)
        _ <- assertIO(seed.masterServiceOffers.size == 30)
        _ <- assertIO(seed.masterServiceOfferVariants.size == 66)
        _ <- assertIO(skipped.isEmpty)
        _ <- assertIO(seed.masterServiceOfferVariants.exists(_.intAttributes.nonEmpty))
        _ <- assertIO(seed.masterServiceOfferVariants.exists(_.bigDecimalAttributes.nonEmpty))
        _ <- assertIO(seed.masterServiceOfferVariants.exists(_.enumAttributes.nonEmpty))
        _ <- assertIO(seed.masterServiceOfferVariants.exists(_.booleanAttributes.nonEmpty))
        _ <- assertIO(
          seed.serviceVariantSchemas.forall(schema =>
            schema.items.forall(item => AttributeDefinition.fromCode(item.attribute.code).nonEmpty)
          )
        )
      } yield ()
    }

    "insert seed into repositories and keep known records readable" in {
      (
        inserter: BeautyQSeedInserter[IO],
        categories: Categories[IO],
        services: Services[IO],
        masters: Masters[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          seed <- loadSeedOrDie
          _ <- ensureSeedLoaded(seed, inserter, variants)
          rootCategory <- categories.getCategory(Category.rootCategoryId)
          _ <- assertIO(rootCategory.isEmpty)
          category <- categories.getCategory(knownCategoryId)
          _ <- assertIO(category.nonEmpty)
          service <- services.getService(knownServiceId)
          _ <- assertIO(service.nonEmpty)
          master <- masters.getMaster(knownMasterId)
          _ <- assertIO(master.nonEmpty)
          location <- masterLocations.getMasterLocation(knownLocationId)
          _ <- assertIO(location.nonEmpty)
          offer <- offers.getMasterServiceOffer(knownOfferId)
          _ <- assertIO(offer.nonEmpty)
          variant <- variants.getMasterServiceOfferVariant(knownVariantId)
          _ <- assertIO(variant.nonEmpty)
        } yield ()
    }

    "roundtrip selected attribute groups for loaded variants" in {
      (
        inserter: BeautyQSeedInserter[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          seed <- loadSeedOrDie
          _ <- ensureSeedLoaded(seed, inserter, variants)
          loadedVariants <- fetchLoadedVariants(seed, variants)
          seedHasInt = seed.masterServiceOfferVariants.exists(_.intAttributes.nonEmpty)
          seedHasBigDecimal = seed.masterServiceOfferVariants.exists(_.bigDecimalAttributes.nonEmpty)
          seedHasEnum = seed.masterServiceOfferVariants.exists(_.enumAttributes.nonEmpty)
          seedHasBoolean = seed.masterServiceOfferVariants.exists(_.booleanAttributes.nonEmpty)
          loadedHasInt = loadedVariants.exists(_.intAttributes.nonEmpty)
          loadedHasBigDecimal = loadedVariants.exists(_.bigDecimalAttributes.nonEmpty)
          loadedHasEnum = loadedVariants.exists(_.enumAttributes.nonEmpty)
          loadedHasBoolean = loadedVariants.exists(_.booleanAttributes.nonEmpty)
          _ <- assertIO(seedHasInt)
          _ <- assertIO(seedHasBigDecimal)
          _ <- assertIO(seedHasEnum)
          _ <- assertIO(seedHasBoolean)
          _ <- assertIO(loadedHasInt)
          _ <- assertIO(loadedHasBigDecimal)
          _ <- assertIO(loadedHasEnum)
          _ <- assertIO(loadedHasBoolean)
        } yield ()
    }

    "validate loaded variants against current service schemas" in {
      (
        inserter: BeautyQSeedInserter[IO],
        variants: MasterServiceOfferVariants[IO],
        offers: MasterServiceOffers[IO],
        schemas: ServiceVariantSchemas[IO],
      ) =>
        for {
          seed <- loadSeedOrDie
          _ <- ensureSeedLoaded(seed, inserter, variants)
          loadedVariants <- fetchLoadedVariants(seed, variants)
          _ <- ZIO.foreachDiscard(loadedVariants) {
            variant =>
              for {
                offer <- offers.getMasterServiceOffer(variant.masterServiceOfferId).flatMap {
                  case Some(value) =>
                    ZIO.succeed(value)
                  case None =>
                    ZIO.fail(QueryFailure.domain(s"MasterServiceOffer ${variant.masterServiceOfferId} is missing for variant ${variant.id}"))
                }
                schema <- schemas.getServiceVariantSchema(offer.serviceId)
                validationResult = schema.validate(variant.attributes)
                _ <- validationResult match {
                  case Right(_) =>
                    ZIO.unit
                  case Left(error) =>
                    ZIO.fail(QueryFailure.operation("validate-seed-variant-schema", s"Variant ${variant.id} failed schema validation: $error"))
                }
              } yield ()
          }
        } yield ()
    }
  }
}

final class BeautyQSeedDummySpec extends BeautyQSeedSpec with DummyTest

final class BeautyQSeedProdSpec extends BeautyQSeedSpec with ProdTest
