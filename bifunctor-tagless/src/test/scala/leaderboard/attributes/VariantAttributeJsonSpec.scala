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

abstract class VariantAttributeJsonSpec extends LeaderboardTest with VariantTestFixtures {
  "VariantAttributeJson" should {
    "decode rejects additional attribute code in wrong typed section" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "intAttributes"        -> Json.obj(
              "deposit_amount" -> 3.asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.isLeft)
        } yield ()
    }

    "decode supports enum attributes from string codes" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "enumAttributes"       -> Json.obj(
              "hair_removal_method" -> "sugaring".asJson,
              "nail_coating_type"   -> "gel_polish".asJson,
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.exists(_.enumAttributes.get(AttributeDefinition.HairRemovalMethodAttribute).contains(HairRemovalMethod.Sugaring)))
          _     <- assertIO(result.exists(_.enumAttributes.get(AttributeDefinition.NailCoatingTypeAttribute).contains(NailCoatingType.GelPolish)))
        } yield ()
    }

    "decode and encode supports boolean attributes as JSON booleans" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "booleanAttributes"    -> Json.obj(
              "with_removal" -> true.asJson,
              "with_design"  -> false.asJson,
            ),
          )
          decoded = json.as[MasterServiceOfferVariant]
          _      <- assertIO(decoded.exists(_.booleanAttributes.get(AttributeDefinition.WithRemoval).contains(true)))
          _      <- assertIO(decoded.exists(_.booleanAttributes.get(AttributeDefinition.WithDesign).contains(false)))
          encoded = decoded.toOption.get.asJson
          _      <- assertIO(encoded.hcursor.downField("booleanAttributes").downField("with_removal").as[Boolean].contains(true))
          _      <- assertIO(encoded.hcursor.downField("booleanAttributes").downField("with_design").as[Boolean].contains(false))
        } yield ()
    }

    "decode and encode supports new enum attributes as strings" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "enumAttributes"       -> Json.obj(
              "nail_service_type"     -> "manicure".asJson,
              "lash_service_type"     -> "extension".asJson,
              "lash_volume"           -> "volume2_d".asJson,
              "brow_service_type"     -> "lamination".asJson,
              "pmu_area"              -> "brows".asJson,
              "facial_treatment_type" -> "microneedling".asJson,
              "body_area"             -> "upper_lip".asJson,
            ),
          )
          decoded = json.as[MasterServiceOfferVariant]
          _      <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.NailServiceTypeAttribute).contains(NailServiceType.Manicure)))
          _      <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.LashServiceTypeAttribute).contains(LashServiceType.Extension)))
          _      <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.LashVolumeAttribute).contains(LashVolume.Volume2D)))
          _      <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.BrowServiceTypeAttribute).contains(BrowServiceType.Lamination)))
          _      <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.PmuAreaAttribute).contains(PmuArea.Brows)))
          _      <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.FacialTreatmentTypeAttribute).contains(FacialTreatmentType.Microneedling)))
          _      <- assertIO(decoded.exists(_.enumAttributes.get(AttributeDefinition.BodyAreaAttribute).contains(BodyArea.UpperLip)))
          encoded = decoded.toOption.get.asJson
          _      <- assertIO(encoded.hcursor.downField("enumAttributes").downField("nail_service_type").as[String].contains("manicure"))
          _      <- assertIO(encoded.hcursor.downField("enumAttributes").downField("lash_service_type").as[String].contains("extension"))
          _      <- assertIO(encoded.hcursor.downField("enumAttributes").downField("lash_volume").as[String].contains("volume2_d"))
          _      <- assertIO(encoded.hcursor.downField("enumAttributes").downField("brow_service_type").as[String].contains("lamination"))
          _      <- assertIO(encoded.hcursor.downField("enumAttributes").downField("pmu_area").as[String].contains("brows"))
          _      <- assertIO(encoded.hcursor.downField("enumAttributes").downField("facial_treatment_type").as[String].contains("microneedling"))
          _      <- assertIO(encoded.hcursor.downField("enumAttributes").downField("body_area").as[String].contains("upper_lip"))
        } yield ()
    }

    "encode emits enum attributes as string codes" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          variant    <- makeVariant(
            variantId,
            offerId,
            locationId,
            BigDecimal("30.0000"),
            BigDecimal("45.0000"),
            60,
            enumAttributes = enumAttributeMap(
              AttributeDefinition.HairRemovalMethodAttribute -> HairRemovalMethod.Sugaring,
              AttributeDefinition.NailCoatingTypeAttribute   -> NailCoatingType.GelPolish,
            ),
          )
          json = variant.asJson
          _   <- assertIO(
            json.hcursor.downField("enumAttributes").downField("hair_removal_method").as[String].contains("sugaring")
          )
          _ <- assertIO(
            json.hcursor.downField("enumAttributes").downField("nail_coating_type").as[String].contains("gel_polish")
          )
        } yield ()
    }

    "decode rejects unknown enum string code" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "enumAttributes"       -> Json.obj(
              "hair_removal_method" -> "unknown_method".asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.isLeft)
        } yield ()
    }

    "decode rejects enum attribute in intAttributes group" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "intAttributes"        -> Json.obj(
              "hair_removal_method" -> 2.asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.isLeft)
        } yield ()
    }

    "decode rejects boolean attribute in intAttributes group" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "intAttributes"        -> Json.obj(
              "with_removal" -> 1.asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(
            result.left.exists(
              error =>
                error.getMessage.contains("with_removal") &&
                error.getMessage.contains("intAttributes") &&
                error.getMessage.contains("Int")
            )
          )
        } yield ()
    }

    "decode rejects int attribute in enumAttributes group" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "enumAttributes"       -> Json.obj(
              "session_count" -> "3".asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.isLeft)
        } yield ()
    }

    "decode rejects int attribute in booleanAttributes group" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "booleanAttributes"    -> Json.obj(
              "session_count" -> true.asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(
            result.left.exists(
              error =>
                error.getMessage.contains("session_count") &&
                error.getMessage.contains("booleanAttributes") &&
                error.getMessage.contains("Boolean")
            )
          )
        } yield ()
    }

    "decode rejects enum attribute in booleanAttributes group" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "booleanAttributes"    -> Json.obj(
              "hair_removal_method" -> true.asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(
            result.left.exists(
              error =>
                error.getMessage.contains("hair_removal_method") &&
                error.getMessage.contains("booleanAttributes") &&
                error.getMessage.contains("Boolean")
            )
          )
        } yield ()
    }

    "decode rejects boolean attribute in enumAttributes group" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "enumAttributes"       -> Json.obj(
              "with_removal" -> "true".asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(
            result.left.exists(
              error =>
                error.getMessage.contains("with_removal") &&
                error.getMessage.contains("enumAttributes") &&
                error.getMessage.contains("Enum")
            )
          )
        } yield ()
    }

    "decode rejects unknown additional attribute code" in {
      (rnd: Rnd[IO]) =>
        for {
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          json        = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "intAttributes"        -> Json.obj(
              "unknown_attribute_code" -> 3.asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.isLeft)
        } yield ()
    }

    "reject additional attribute stored in wrong typed storage" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"variant-type-category-$categoryId")
          master      = Master(masterId, s"variant-type-master-$masterId")
          service     = Service(serviceId, categoryId, s"variant-type-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"variant-type-location-$locationId",
            s"variant-type-address-$locationId",
            BigDecimal("10.0000"),
            BigDecimal("20.0000"),
          )
          schema = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false))
          _     <- categories.upsertCategory(category)
          _     <- masters.upsertMaster(master)
          _     <- services.upsertService(service)
          _     <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _     <- offers.upsertMasterServiceOffer(offer)
          _     <- masterLocations.upsertMasterLocation(location)
          json   = Json.obj(
            "id"                   -> variantId.asJson,
            "masterServiceOfferId" -> offerId.asJson,
            "masterLocationId"     -> locationId.asJson,
            "priceFrom"            -> BigDecimal("30.0000").asJson,
            "priceTo"              -> BigDecimal("45.0000").asJson,
            "durationMin"          -> 60.asJson,
            "intAttributes"        -> Json.obj(
              "deposit_amount" -> 3.asJson
            ),
          )
          result = json.as[MasterServiceOfferVariant]
          _     <- assertIO(result.isLeft)
        } yield ()
    }

  }
}

class VariantAttributeJsonSpecDummy extends VariantAttributeJsonSpec with DummyTest
class VariantAttributeJsonSpecPostgres extends VariantAttributeJsonSpec with ProdTest
