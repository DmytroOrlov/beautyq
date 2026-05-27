package leaderboard

import distage.{DIKey, ModuleDef, Scene}
import io.circe.Json
import io.circe.syntax.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import izumi.distage.model.definition.Activation
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import leaderboard.model.*
import leaderboard.repo.{Categories, Ladder, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, Profiles, ServiceVariantSchemas, Services}
import leaderboard.services.Ranks
import leaderboard.sql.SQL
import leaderboard.zioenv.*
import zio.{IO, ZIO}
import leaderboard.model.AttributeMap

// AI-NOTE: For distage testkit memoizationRoots, Activation, and BIO patterns used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md
abstract class LeaderboardTest extends SpecZIO with AssertZIO {
  override def config = super.config.copy(
    pluginConfig    = PluginConfig.cached(packagesEnabled = Seq("leaderboard.plugins")),
    moduleOverrides = super.config.moduleOverrides ++ new ModuleDef {
      make[Rnd[IO]].from[Rnd.Impl[IO]]
    },
    // For testing, set up a docker container with postgres,
    // instead of trying to connect to an external database
    activation = Activation(Scene -> Scene.Managed),
    // Instantiate repos only once per test-run and
    // share them and all their dependencies across all tests.
    // this includes the Postgres Docker container above and table DDLs
    memoizationRoots = Set(
      DIKey[Ladder[IO]],
      DIKey[Profiles[IO]],
      DIKey[Categories[IO]],
      DIKey[Masters[IO]],
      DIKey[MasterLocations[IO]],
      DIKey[MasterServiceOffers[IO]],
      DIKey[ServiceVariantSchemas[IO]],
      DIKey[MasterServiceOfferVariants[IO]],
      DIKey[Services[IO]],
    ),
  )
}

trait DummyTest extends LeaderboardTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Repo -> Repo.Dummy)
  )
}

trait ProdTest extends LeaderboardTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Repo -> Repo.Prod)
  )
}

trait VariantTestFixtures {
  protected def enumAttributeMap(
    entries: (EnumAttributeDefinition[?], CodedEnumValue)*
  ): AttributeMap[CodedEnumValue] =
    entries.foldLeft(AttributeMap.Impl[CodedEnumValue, AttributeDefinition[CodedEnumValue]](Map.empty)) {
      case (acc, (definition, value)) =>
        acc.updated(definition.asInstanceOf[AttributeDefinition[CodedEnumValue]], value)
    }

  protected def makeVariant(
    id: MasterServiceOfferVariantId,
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
    priceFrom: BigDecimal                          = BigDecimal("30.0000"),
    priceTo: BigDecimal                            = BigDecimal("45.0000"),
    durationMin: Int                               = 60,
    intAttributes: AttributeMap[Int]               = AttributeMap.empty,
    bigDecimalAttributes: AttributeMap[BigDecimal] = AttributeMap.empty,
    enumAttributes: AttributeMap[CodedEnumValue]   = AttributeMap.empty,
    booleanAttributes: AttributeMap[Boolean]       = AttributeMap.empty,
  ): IO[QueryFailure, MasterServiceOfferVariant] =
    MasterServiceOfferVariant
      .make(
        id,
        masterServiceOfferId,
        masterLocationId,
        priceFrom,
        priceTo,
        durationMin,
        MasterServiceOfferVariantAttributes(intAttributes, bigDecimalAttributes, enumAttributes, booleanAttributes),
      ) match {
      case Right(value) =>
        ZIO.succeed(value)
      case Left(error) =>
        ZIO.fail(QueryFailure.operation("make-master-service-offer-variant", error.message))
    }

  protected def makeSchema(serviceId: ServiceId, items: ServiceVariantSchemaItem*): ServiceVariantSchema =
    ServiceVariantSchema.fromItems(serviceId, items)
}
