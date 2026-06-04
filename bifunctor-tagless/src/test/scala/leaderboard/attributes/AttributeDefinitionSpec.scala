package leaderboard

import leaderboard.model.*
import zio.{IO, ZIO}

// AI-NOTE: For distage testkit memoizationRoots, Activation, and BIO patterns used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md

abstract class AttributeDefinitionSpec extends LeaderboardTest with VariantTestFixtures {
  "AttributeDefinition" should {
    "attribute definition registry resolves all new enum attribute codes" in {
      for {
        _ <- assertIO(AttributeDefinition.fromCode("nail_service_type").contains(AttributeDefinition.NailServiceTypeAttribute))
        _ <- assertIO(AttributeDefinition.fromCode("lash_service_type").contains(AttributeDefinition.LashServiceTypeAttribute))
        _ <- assertIO(AttributeDefinition.fromCode("lash_volume").contains(AttributeDefinition.LashVolumeAttribute))
        _ <- assertIO(AttributeDefinition.fromCode("brow_service_type").contains(AttributeDefinition.BrowServiceTypeAttribute))
        _ <- assertIO(AttributeDefinition.fromCode("pmu_area").contains(AttributeDefinition.PmuAreaAttribute))
        _ <- assertIO(AttributeDefinition.fromCode("facial_treatment_type").contains(AttributeDefinition.FacialTreatmentTypeAttribute))
        _ <- assertIO(AttributeDefinition.fromCode("body_area").contains(AttributeDefinition.BodyAreaAttribute))
        _ <- assertIO(AttributeDefinition.fromCodeAsEnum("nail_service_type").contains(AttributeDefinition.NailServiceTypeAttribute))
        _ <- assertIO(AttributeDefinition.fromCodeAsEnum("lash_service_type").contains(AttributeDefinition.LashServiceTypeAttribute))
        _ <- assertIO(AttributeDefinition.fromCodeAsEnum("lash_volume").contains(AttributeDefinition.LashVolumeAttribute))
        _ <- assertIO(AttributeDefinition.fromCodeAsEnum("brow_service_type").contains(AttributeDefinition.BrowServiceTypeAttribute))
        _ <- assertIO(AttributeDefinition.fromCodeAsEnum("pmu_area").contains(AttributeDefinition.PmuAreaAttribute))
        _ <- assertIO(AttributeDefinition.fromCodeAsEnum("facial_treatment_type").contains(AttributeDefinition.FacialTreatmentTypeAttribute))
        _ <- assertIO(AttributeDefinition.fromCodeAsEnum("body_area").contains(AttributeDefinition.BodyAreaAttribute))
        _ <- assertIO(AttributeDefinition.fromCode("with_removal").contains(AttributeDefinition.WithRemoval))
        _ <- assertIO(AttributeDefinition.fromCode("with_design").contains(AttributeDefinition.WithDesign))
        _ <- assertIO(AttributeDefinition.fromCode("with_tinting").contains(AttributeDefinition.WithTinting))
        _ <- assertIO(AttributeDefinition.fromCode("with_correction").contains(AttributeDefinition.WithCorrection))
        _ <- assertIO(AttributeDefinition.fromCodeAsBoolean("with_removal").contains(AttributeDefinition.WithRemoval))
        _ <- assertIO(AttributeDefinition.fromCodeAsBoolean("with_design").contains(AttributeDefinition.WithDesign))
        _ <- assertIO(AttributeDefinition.fromCodeAsBoolean("with_tinting").contains(AttributeDefinition.WithTinting))
        _ <- assertIO(AttributeDefinition.fromCodeAsBoolean("with_correction").contains(AttributeDefinition.WithCorrection))
      } yield ()
    }

    "attributes expose typed access by definition and typed views" in {
      (rnd: Rnd[IO]) =>
        for {
          variantId  <- rnd[MasterServiceOfferVariantId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          attributes  = MasterServiceOfferVariantAttributes(
            intValues        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 3)),
            bigDecimalValues = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("12.5000"))),
            booleanValues    = AttributeMap.Impl(Map(AttributeDefinition.WithRemoval -> true)),
          )
          variant <- ZIO
            .fromEither(
              MasterServiceOfferVariant.make(
                variantId,
                offerId,
                locationId,
                BigDecimal("20.0000"),
                BigDecimal("30.0000"),
                60,
                attributes,
              )
            )
            .mapError(error => QueryFailure.operation("make-master-service-offer-variant", error.message))
          _ <- assertIO(variant.getAttribute(AttributeDefinition.SessionCount).contains(3))
          _ <- assertIO(
            variant.getAttribute(AttributeDefinition.DepositAmount).contains(BigDecimal("12.5000"))
          )
          _ <- assertIO(variant.intAttributes.get(AttributeDefinition.SessionCount).contains(3))
          _ <- assertIO(
            variant.bigDecimalAttributes.get(AttributeDefinition.DepositAmount).contains(BigDecimal("12.5000"))
          )
          _ <- assertIO(variant.booleanAttributes.get(AttributeDefinition.WithRemoval).contains(true))
          _ <- assertIO(variant.intAttributes.keySet == Set(AttributeDefinition.SessionCount))
          _ <- assertIO(
            variant.bigDecimalAttributes.keySet == Set(
              AttributeDefinition.DepositAmount
            )
          )
          _ <- assertIO(variant.enumAttributes.get(AttributeDefinition.HairRemovalMethodAttribute).isEmpty)
          _ <- assertIO(variant.booleanAttributes.keySet == Set(AttributeDefinition.WithRemoval))
        } yield ()
    }

  }
}

class AttributeDefinitionSpecDummy extends AttributeDefinitionSpec with DummyTest
class AttributeDefinitionSpecPostgres extends AttributeDefinitionSpec with ProdTest
