package leaderboard

import leaderboard.model.*
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import doobie.implicits.*
import doobie.postgres.implicits.*
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import leaderboard.sql.SQL
import zio.{IO, ZIO}

// AI-NOTE: For distage testkit memoizationRoots, Activation, and BIO patterns used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md

abstract class VariantAttributeStorageSpec extends LeaderboardTest with VariantTestFixtures {
  "VariantAttributeStorage" should {
    "reject enum value that does not belong to enum attribute" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"variant-enum-mismatch-category-$categoryId")
          master      = Master(masterId, s"variant-enum-mismatch-master-$masterId")
          service     = Service(serviceId, categoryId, s"variant-enum-mismatch-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"variant-enum-mismatch-location-$locationId",
            s"variant-enum-mismatch-address-$locationId",
            BigDecimal("10.0000"),
            BigDecimal("20.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.NailCoatingTypeAttribute, false))
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            BigDecimal("30.0000"),
            BigDecimal("45.0000"),
            60,
            enumAttributes = enumAttributeMap(
              AttributeDefinition.NailCoatingTypeAttribute -> HairRemovalMethod.Sugaring
            ),
          )
          _      <- categories.upsertCategory(category)
          _      <- masters.upsertMaster(master)
          _      <- services.upsertService(service)
          _      <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _      <- offers.upsertMasterServiceOffer(offer)
          _      <- masterLocations.upsertMasterLocation(location)
          result <- variants.upsertMasterServiceOfferVariant(variant).either
          _      <- assertIO(
            result.left.exists {
              case QueryFailure.OperationFailure(operationName, message) =>
                operationName == "upsert-master-service-offer-variant" &&
                message.contains("nail_coating_type") &&
                message.contains("sugaring") &&
                message.contains("does not accept enum value")
              case _ =>
                false
            }
          )
        } yield ()
    }

  }
}

abstract class MasterServiceOfferVariantsStorageValidationTest extends LeaderboardTest {
  private def makeSchema(serviceId: ServiceId, items: ServiceVariantSchemaItem*): ServiceVariantSchema =
    ServiceVariantSchema.fromItems(serviceId, items)

  private def enumAttributeMap(
    entries: (EnumAttributeDefinition[?], CodedEnumValue)*
  ): AttributeMap[CodedEnumValue] =
    entries.foldLeft(AttributeMap.Impl[CodedEnumValue, AttributeDefinition[CodedEnumValue]](Map.empty)) {
      case (acc, (definition, value)) =>
        acc.updated(definition.asInstanceOf[AttributeDefinition[CodedEnumValue]], value)
    }

  private def makeVariant(
    id: MasterServiceOfferVariantId,
    masterServiceOfferId: MasterServiceOfferId,
    masterLocationId: MasterLocationId,
    intAttributes: AttributeMap[Int]               = AttributeMap.empty,
    bigDecimalAttributes: AttributeMap[BigDecimal] = AttributeMap.empty,
    enumAttributes: AttributeMap[CodedEnumValue]   = AttributeMap.empty,
    booleanAttributes: AttributeMap[Boolean]       = AttributeMap.empty,
  ): IO[QueryFailure, MasterServiceOfferVariant] =
    ZIO
      .fromEither(
        MasterServiceOfferVariant.make(
          id,
          masterServiceOfferId,
          masterLocationId,
          BigDecimal("30.0000"),
          BigDecimal("45.0000"),
          60,
          MasterServiceOfferVariantAttributes(intAttributes, bigDecimalAttributes, enumAttributes, booleanAttributes),
        )
      )
      .mapError(error => QueryFailure.operation("make-master-service-offer-variant", error.message))

  "MasterServiceOfferVariants numeric attribute storage" should {
    "store Int attributes in the numeric table and load them back as typed Int values" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-int-category-$categoryId")
          master      = Master(masterId, s"numeric-int-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-int-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-int-location-$locationId",
            s"numeric-int-address-$locationId",
            BigDecimal("1.0000"),
            BigDecimal("2.0000"),
          )
          schema      = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          variant    <- makeVariant(variantId, offerId, locationId, intAttributes = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 3)))
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant)
          loaded     <- variants.getMasterServiceOfferVariant(variantId)
          storedRows <- db.execute("count-master-service-offer-variant-numeric-attributes-int") {
            sql"""select count(*)
                                from master_service_offer_variant_numeric_attributes
                                where master_service_offer_variant_id = $variantId
                              """.query[Long].unique
          }
          _ <- assertIO(loaded.flatMap(_.intAttributes.get(AttributeDefinition.SessionCount)).contains(3))
          _ <- assertIO(storedRows == 1L)
        } yield ()
    }

    "store BigDecimal attributes in the numeric table and load them back as typed BigDecimal values" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-decimal-category-$categoryId")
          master      = Master(masterId, s"numeric-decimal-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-decimal-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-decimal-location-$locationId",
            s"numeric-decimal-address-$locationId",
            BigDecimal("3.0000"),
            BigDecimal("4.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false))
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("25.5000"))),
          )
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant)
          loaded     <- variants.getMasterServiceOfferVariant(variantId)
          storedRows <- db.execute("count-master-service-offer-variant-numeric-attributes-decimal") {
            sql"""select count(*)
                               from master_service_offer_variant_numeric_attributes
                               where master_service_offer_variant_id = $variantId
                             """.query[Long].unique
          }
          _ <- assertIO(loaded.flatMap(_.bigDecimalAttributes.get(AttributeDefinition.DepositAmount)).contains(BigDecimal("25.5000")))
          _ <- assertIO(storedRows == 1L)
        } yield ()
    }

    "store mixed Int and BigDecimal attributes in one numeric table and restore separate typed maps" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-mixed-category-$categoryId")
          master      = Master(masterId, s"numeric-mixed-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-mixed-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-mixed-location-$locationId",
            s"numeric-mixed-address-$locationId",
            BigDecimal("5.0000"),
            BigDecimal("6.0000"),
          )
          schema = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false),
            ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false),
          )
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 3)),
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("25.5000"))),
          )
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant)
          loaded     <- variants.getMasterServiceOfferVariant(variantId)
          storedRows <- db.execute("select-master-service-offer-variant-numeric-attributes-mixed") {
            sql"""select attribute_code, value
                               from master_service_offer_variant_numeric_attributes
                               where master_service_offer_variant_id = $variantId
                               order by attribute_code asc
                             """.query[(String, BigDecimal)].to[List]
          }
          _ <- assertIO(loaded.flatMap(_.intAttributes.get(AttributeDefinition.SessionCount)).contains(3))
          _ <- assertIO(loaded.flatMap(_.bigDecimalAttributes.get(AttributeDefinition.DepositAmount)).contains(BigDecimal("25.5000")))
          _ <- assertIO(
            storedRows == List(
              "deposit_amount" -> BigDecimal("25.5000"),
              "session_count"  -> BigDecimal("3"),
            )
          )
        } yield ()
    }

    "store HairRemovalMethod enum attribute in numeric table and load it back as typed enum value" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-enum-hair-category-$categoryId")
          master      = Master(masterId, s"numeric-enum-hair-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-enum-hair-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-enum-hair-location-$locationId",
            s"numeric-enum-hair-address-$locationId",
            BigDecimal("15.0000"),
            BigDecimal("16.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, false))
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            enumAttributes = enumAttributeMap(
              AttributeDefinition.HairRemovalMethodAttribute -> HairRemovalMethod.Sugaring
            ),
          )
          _           <- categories.upsertCategory(category)
          _           <- masters.upsertMaster(master)
          _           <- services.upsertService(service)
          _           <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _           <- offers.upsertMasterServiceOffer(offer)
          _           <- masterLocations.upsertMasterLocation(location)
          _           <- variants.upsertMasterServiceOfferVariant(variant)
          loaded      <- variants.getMasterServiceOfferVariant(variantId)
          storedValue <- db.execute("select-master-service-offer-variant-hair-removal-method") {
            sql"""select value
                                from master_service_offer_variant_numeric_attributes
                                where master_service_offer_variant_id = $variantId
                                  and attribute_code = ${AttributeDefinition.HairRemovalMethodAttribute.code}
                              """.query[BigDecimal].unique
          }
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.HairRemovalMethodAttribute)).contains(HairRemovalMethod.Sugaring))
          _ <- assertIO(storedValue == BigDecimal("2"))
        } yield ()
    }

    "store NailCoatingType enum attribute in numeric table and load it back as typed enum value" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-enum-nail-category-$categoryId")
          master      = Master(masterId, s"numeric-enum-nail-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-enum-nail-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-enum-nail-location-$locationId",
            s"numeric-enum-nail-address-$locationId",
            BigDecimal("17.0000"),
            BigDecimal("18.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.NailCoatingTypeAttribute, false))
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            enumAttributes = enumAttributeMap(
              AttributeDefinition.NailCoatingTypeAttribute -> NailCoatingType.GelPolish
            ),
          )
          _           <- categories.upsertCategory(category)
          _           <- masters.upsertMaster(master)
          _           <- services.upsertService(service)
          _           <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _           <- offers.upsertMasterServiceOffer(offer)
          _           <- masterLocations.upsertMasterLocation(location)
          _           <- variants.upsertMasterServiceOfferVariant(variant)
          loaded      <- variants.getMasterServiceOfferVariant(variantId)
          storedValue <- db.execute("select-master-service-offer-variant-nail-coating-type") {
            sql"""select value
                                from master_service_offer_variant_numeric_attributes
                                where master_service_offer_variant_id = $variantId
                                  and attribute_code = ${AttributeDefinition.NailCoatingTypeAttribute.code}
                              """.query[BigDecimal].unique
          }
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.NailCoatingTypeAttribute)).contains(NailCoatingType.GelPolish))
          _ <- assertIO(storedValue == BigDecimal("3"))
        } yield ()
    }

    "store mixed Int, BigDecimal, and enum attributes in one numeric table and restore typed maps" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-mixed-all-category-$categoryId")
          master      = Master(masterId, s"numeric-mixed-all-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-mixed-all-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-mixed-all-location-$locationId",
            s"numeric-mixed-all-address-$locationId",
            BigDecimal("19.0000"),
            BigDecimal("20.0000"),
          )
          schema = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false),
            ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false),
            ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.NailCoatingTypeAttribute, false),
          )
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 3)),
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("25.5000"))),
            enumAttributes       = enumAttributeMap(
              AttributeDefinition.HairRemovalMethodAttribute -> HairRemovalMethod.Sugaring,
              AttributeDefinition.NailCoatingTypeAttribute   -> NailCoatingType.GelPolish,
            ),
          )
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant)
          loaded     <- variants.getMasterServiceOfferVariant(variantId)
          storedRows <- db.execute("select-master-service-offer-variant-numeric-attributes-mixed-all") {
            sql"""select attribute_code, value
                               from master_service_offer_variant_numeric_attributes
                               where master_service_offer_variant_id = $variantId
                               order by attribute_code asc
                             """.query[(String, BigDecimal)].to[List]
          }
          _ <- assertIO(loaded.flatMap(_.intAttributes.get(AttributeDefinition.SessionCount)).contains(3))
          _ <- assertIO(loaded.flatMap(_.bigDecimalAttributes.get(AttributeDefinition.DepositAmount)).contains(BigDecimal("25.5000")))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.HairRemovalMethodAttribute)).contains(HairRemovalMethod.Sugaring))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.NailCoatingTypeAttribute)).contains(NailCoatingType.GelPolish))
          _ <- assertIO(
            storedRows == List(
              "deposit_amount"      -> BigDecimal("25.5000"),
              "hair_removal_method" -> BigDecimal("2"),
              "nail_coating_type"   -> BigDecimal("3"),
              "session_count"       -> BigDecimal("3"),
            )
          )
        } yield ()
    }

    "store Boolean attributes in the numeric table and load them back as typed Boolean values" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-bool-category-$categoryId")
          master      = Master(masterId, s"numeric-bool-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-bool-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-bool-location-$locationId",
            s"numeric-bool-address-$locationId",
            BigDecimal("25.0000"),
            BigDecimal("26.0000"),
          )
          schema = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.WithRemoval, false),
            ServiceVariantSchemaItem(AttributeDefinition.WithDesign, false),
          )
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            booleanAttributes = AttributeMap.Impl(Map(AttributeDefinition.WithRemoval -> true, AttributeDefinition.WithDesign -> false)),
          )
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant)
          loaded     <- variants.getMasterServiceOfferVariant(variantId)
          storedRows <- db.execute("select-master-service-offer-variant-boolean") {
            sql"""select attribute_code, value
                               from master_service_offer_variant_numeric_attributes
                               where master_service_offer_variant_id = $variantId
                               order by attribute_code asc
                             """.query[(String, BigDecimal)].to[List]
          }
          _ <- assertIO(loaded.flatMap(_.booleanAttributes.get(AttributeDefinition.WithRemoval)).contains(true))
          _ <- assertIO(loaded.flatMap(_.booleanAttributes.get(AttributeDefinition.WithDesign)).contains(false))
          _ <- assertIO(
            storedRows == List(
              "with_design"  -> BigDecimal("0"),
              "with_removal" -> BigDecimal("1"),
            )
          )
        } yield ()
    }

    "store and load several new enum attributes through numeric table" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-new-enums-category-$categoryId")
          master      = Master(masterId, s"numeric-new-enums-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-new-enums-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-new-enums-location-$locationId",
            s"numeric-new-enums-address-$locationId",
            BigDecimal("21.0000"),
            BigDecimal("22.0000"),
          )
          schema = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.NailServiceTypeAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.LashServiceTypeAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.LashVolumeAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.BrowServiceTypeAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.PmuAreaAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.FacialTreatmentTypeAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.BodyAreaAttribute, false),
          )
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            enumAttributes = enumAttributeMap(
              AttributeDefinition.NailServiceTypeAttribute     -> NailServiceType.Manicure,
              AttributeDefinition.LashServiceTypeAttribute     -> LashServiceType.Extension,
              AttributeDefinition.LashVolumeAttribute          -> LashVolume.Volume2D,
              AttributeDefinition.BrowServiceTypeAttribute     -> BrowServiceType.Lamination,
              AttributeDefinition.PmuAreaAttribute             -> PmuArea.Brows,
              AttributeDefinition.FacialTreatmentTypeAttribute -> FacialTreatmentType.Microneedling,
              AttributeDefinition.BodyAreaAttribute            -> BodyArea.UpperLip,
            ),
          )
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant)
          loaded     <- variants.getMasterServiceOfferVariant(variantId)
          storedRows <- db.execute("select-master-service-offer-variant-numeric-attributes-new-enums") {
            sql"""select attribute_code, value
                               from master_service_offer_variant_numeric_attributes
                               where master_service_offer_variant_id = $variantId
                               order by attribute_code asc
                             """.query[(String, BigDecimal)].to[List]
          }
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.NailServiceTypeAttribute)).contains(NailServiceType.Manicure))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.LashServiceTypeAttribute)).contains(LashServiceType.Extension))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.LashVolumeAttribute)).contains(LashVolume.Volume2D))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.BrowServiceTypeAttribute)).contains(BrowServiceType.Lamination))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.PmuAreaAttribute)).contains(PmuArea.Brows))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.FacialTreatmentTypeAttribute)).contains(FacialTreatmentType.Microneedling))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.BodyAreaAttribute)).contains(BodyArea.UpperLip))
          _ <- assertIO(
            storedRows == List(
              "body_area"             -> BigDecimal("1"),
              "brow_service_type"     -> BigDecimal("3"),
              "facial_treatment_type" -> BigDecimal("6"),
              "lash_service_type"     -> BigDecimal("1"),
              "lash_volume"           -> BigDecimal("2"),
              "nail_service_type"     -> BigDecimal("1"),
              "pmu_area"              -> BigDecimal("1"),
            )
          )
        } yield ()
    }

    "store mixed Int, BigDecimal, enum, and Boolean attributes in one numeric table and restore typed maps" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-mixed-full-category-$categoryId")
          master      = Master(masterId, s"numeric-mixed-full-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-mixed-full-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-mixed-full-location-$locationId",
            s"numeric-mixed-full-address-$locationId",
            BigDecimal("27.0000"),
            BigDecimal("28.0000"),
          )
          schema = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false),
            ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false),
            ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, false),
            ServiceVariantSchemaItem(AttributeDefinition.WithRemoval, false),
          )
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 3)),
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("25.5000"))),
            enumAttributes       = enumAttributeMap(
              AttributeDefinition.HairRemovalMethodAttribute -> HairRemovalMethod.Sugaring
            ),
            booleanAttributes = AttributeMap.Impl(Map(AttributeDefinition.WithRemoval -> true)),
          )
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _          <- offers.upsertMasterServiceOffer(offer)
          _          <- masterLocations.upsertMasterLocation(location)
          _          <- variants.upsertMasterServiceOfferVariant(variant)
          loaded     <- variants.getMasterServiceOfferVariant(variantId)
          storedRows <- db.execute("select-master-service-offer-variant-numeric-attributes-mixed-full") {
            sql"""select attribute_code, value
                               from master_service_offer_variant_numeric_attributes
                               where master_service_offer_variant_id = $variantId
                               order by attribute_code asc
                             """.query[(String, BigDecimal)].to[List]
          }
          _ <- assertIO(loaded.flatMap(_.intAttributes.get(AttributeDefinition.SessionCount)).contains(3))
          _ <- assertIO(loaded.flatMap(_.bigDecimalAttributes.get(AttributeDefinition.DepositAmount)).contains(BigDecimal("25.5000")))
          _ <- assertIO(loaded.flatMap(_.enumAttributes.get(AttributeDefinition.HairRemovalMethodAttribute)).contains(HairRemovalMethod.Sugaring))
          _ <- assertIO(loaded.flatMap(_.booleanAttributes.get(AttributeDefinition.WithRemoval)).contains(true))
          _ <- assertIO(
            storedRows == List(
              "deposit_amount"      -> BigDecimal("25.5000"),
              "hair_removal_method" -> BigDecimal("2"),
              "session_count"       -> BigDecimal("3"),
              "with_removal"        -> BigDecimal("1"),
            )
          )
        } yield ()
    }

    "loadMany keeps numeric attributes grouped by variant id" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId  <- rnd[CategoryId]
          masterId    <- rnd[MasterId]
          serviceId   <- rnd[ServiceId]
          offerId     <- rnd[MasterServiceOfferId]
          location1Id <- rnd[MasterLocationId]
          location2Id <- rnd[MasterLocationId]
          variant1Id  <- rnd[MasterServiceOfferVariantId]
          variant2Id  <- rnd[MasterServiceOfferVariantId]
          category     = Category(categoryId, rootCategoryId, 0, s"numeric-loadmany-category-$categoryId")
          master       = Master(masterId, s"numeric-loadmany-master-$masterId")
          service      = Service(serviceId, categoryId, s"numeric-loadmany-service-$serviceId")
          offer        = MasterServiceOffer(offerId, masterId, serviceId)
          location1    = MasterLocation(
            location1Id,
            masterId,
            s"numeric-loadmany-location-a-$location1Id",
            s"numeric-loadmany-address-a-$location1Id",
            BigDecimal("7.0000"),
            BigDecimal("8.0000"),
          )
          location2 = MasterLocation(
            location2Id,
            masterId,
            s"numeric-loadmany-location-b-$location2Id",
            s"numeric-loadmany-address-b-$location2Id",
            BigDecimal("9.0000"),
            BigDecimal("10.0000"),
          )
          schema = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false),
            ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false),
          )
          variant1 <- makeVariant(
            variant1Id,
            offerId,
            location1Id,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 3)),
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("25.5000"))),
          )
          variant2 <- makeVariant(
            variant2Id,
            offerId,
            location2Id,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 7)),
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("41.2500"))),
          )
          _   <- categories.upsertCategory(category)
          _   <- masters.upsertMaster(master)
          _   <- services.upsertService(service)
          _   <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _   <- offers.upsertMasterServiceOffer(offer)
          _   <- masterLocations.upsertMasterLocation(location1)
          _   <- masterLocations.upsertMasterLocation(location2)
          _   <- variants.upsertMasterServiceOfferVariant(variant1)
          _   <- variants.upsertMasterServiceOfferVariant(variant2)
          res <- variants.getMasterServiceOfferVariantsByOffer(offerId)
          _   <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "reject non-integer stored numeric value for an Int attribute definition" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-invalid-int-category-$categoryId")
          master      = Master(masterId, s"numeric-invalid-int-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-invalid-int-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-invalid-int-location-$locationId",
            s"numeric-invalid-int-address-$locationId",
            BigDecimal("11.0000"),
            BigDecimal("12.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          variant <- makeVariant(variantId, offerId, locationId)
          _       <- categories.upsertCategory(category)
          _       <- masters.upsertMaster(master)
          _       <- services.upsertService(service)
          _       <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _       <- offers.upsertMasterServiceOffer(offer)
          _       <- masterLocations.upsertMasterLocation(location)
          _       <- variants.upsertMasterServiceOfferVariant(variant)
          _       <- db.execute("insert-invalid-master-service-offer-variant-numeric-attribute") {
            sql"""insert into master_service_offer_variant_numeric_attributes (
                 |  master_service_offer_variant_id,
                 |  attribute_code,
                 |  value
                 |)
                 |values (
                 |  $variantId,
                 |  ${AttributeDefinition.SessionCount.code},
                 |  ${BigDecimal("3.5")}
                 |)
                 |on conflict (master_service_offer_variant_id, attribute_code) do update set
                 |  value = excluded.value
                 |""".stripMargin.update.run
          }
          result <- variants.getMasterServiceOfferVariant(variantId).either
          _      <- assertIO(
            result.left.exists {
              case QueryFailure.OperationFailure(operationName, message) =>
                operationName == "load-master-service-offer-variant-attributes" &&
                message == s"MasterServiceOfferVariant attribute ${AttributeDefinition.SessionCount.code} expected Int-compatible numeric value but got non-integer numeric value: 3.5"
              case _ =>
                false
            }
          )
        } yield ()
    }

    "fail to load Int attribute when numeric value is outside Int range" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-out-of-range-category-$categoryId")
          master      = Master(masterId, s"numeric-out-of-range-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-out-of-range-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-out-of-range-location-$locationId",
            s"numeric-out-of-range-address-$locationId",
            BigDecimal("13.0000"),
            BigDecimal("14.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          variant <- makeVariant(variantId, offerId, locationId)
          _       <- categories.upsertCategory(category)
          _       <- masters.upsertMaster(master)
          _       <- services.upsertService(service)
          _       <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _       <- offers.upsertMasterServiceOffer(offer)
          _       <- masterLocations.upsertMasterLocation(location)
          _       <- variants.upsertMasterServiceOfferVariant(variant)
          _       <- db.execute("insert-out-of-range-master-service-offer-variant-numeric-attribute") {
            sql"""insert into master_service_offer_variant_numeric_attributes (
                 |  master_service_offer_variant_id,
                 |  attribute_code,
                 |  value
                 |)
                 |values (
                 |  $variantId,
                 |  ${AttributeDefinition.SessionCount.code},
                 |  ${BigDecimal("2147483648")}
                 |)
                 |on conflict (master_service_offer_variant_id, attribute_code) do update set
                 |  value = excluded.value
                 |""".stripMargin.update.run
          }
          result <- variants.getMasterServiceOfferVariant(variantId).either
          _      <- assertIO(
            result.left.exists {
              case QueryFailure.OperationFailure(operationName, message) =>
                operationName == "load-master-service-offer-variant-attributes" &&
                message.contains(AttributeDefinition.SessionCount.code) &&
                message.contains("Int-compatible numeric value") &&
                message.contains("outside Int range") &&
                message.contains("2147483648")
              case _ =>
                false
            }
          )
        } yield ()
    }

    "fail to load Boolean attribute when numeric value is outside boolean range" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-invalid-bool-category-$categoryId")
          master      = Master(masterId, s"numeric-invalid-bool-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-invalid-bool-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-invalid-bool-location-$locationId",
            s"numeric-invalid-bool-address-$locationId",
            BigDecimal("29.0000"),
            BigDecimal("30.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.WithRemoval, false))
          variant <- makeVariant(variantId, offerId, locationId)
          _       <- categories.upsertCategory(category)
          _       <- masters.upsertMaster(master)
          _       <- services.upsertService(service)
          _       <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _       <- offers.upsertMasterServiceOffer(offer)
          _       <- masterLocations.upsertMasterLocation(location)
          _       <- variants.upsertMasterServiceOfferVariant(variant)
          _       <- db.execute("insert-invalid-master-service-offer-variant-boolean-attribute") {
            sql"""insert into master_service_offer_variant_numeric_attributes (
                 |  master_service_offer_variant_id,
                 |  attribute_code,
                 |  value
                 |)
                 |values (
                 |  $variantId,
                 |  ${AttributeDefinition.WithRemoval.code},
                 |  ${BigDecimal("2")}
                 |)
                 |on conflict (master_service_offer_variant_id, attribute_code) do update set
                 |  value = excluded.value
                 |""".stripMargin.update.run
          }
          result <- variants.getMasterServiceOfferVariant(variantId).either
          _      <- assertIO(result.left.exists(_.isInstanceOf[QueryFailure.OperationFailure]))
        } yield ()
    }

    "fail to load Boolean attribute when numeric value is non-integer" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-invalid-bool-decimal-category-$categoryId")
          master      = Master(masterId, s"numeric-invalid-bool-decimal-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-invalid-bool-decimal-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-invalid-bool-decimal-location-$locationId",
            s"numeric-invalid-bool-decimal-address-$locationId",
            BigDecimal("31.0000"),
            BigDecimal("32.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.WithRemoval, false))
          variant <- makeVariant(variantId, offerId, locationId)
          _       <- categories.upsertCategory(category)
          _       <- masters.upsertMaster(master)
          _       <- services.upsertService(service)
          _       <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _       <- offers.upsertMasterServiceOffer(offer)
          _       <- masterLocations.upsertMasterLocation(location)
          _       <- variants.upsertMasterServiceOfferVariant(variant)
          _       <- db.execute("insert-invalid-master-service-offer-variant-boolean-decimal-attribute") {
            sql"""insert into master_service_offer_variant_numeric_attributes (
                 |  master_service_offer_variant_id,
                 |  attribute_code,
                 |  value
                 |)
                 |values (
                 |  $variantId,
                 |  ${AttributeDefinition.WithRemoval.code},
                 |  ${BigDecimal("0.5")}
                 |)
                 |on conflict (master_service_offer_variant_id, attribute_code) do update set
                 |  value = excluded.value
                 |""".stripMargin.update.run
          }
          result <- variants.getMasterServiceOfferVariant(variantId).either
          _      <- assertIO(result.left.exists(_.isInstanceOf[QueryFailure.OperationFailure]))
        } yield ()
    }

    "reject unknown enum int code from numeric storage" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-unknown-enum-code-category-$categoryId")
          master      = Master(masterId, s"numeric-unknown-enum-code-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-unknown-enum-code-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-unknown-enum-code-location-$locationId",
            s"numeric-unknown-enum-code-address-$locationId",
            BigDecimal("21.0000"),
            BigDecimal("22.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, false))
          variant <- makeVariant(variantId, offerId, locationId)
          _       <- categories.upsertCategory(category)
          _       <- masters.upsertMaster(master)
          _       <- services.upsertService(service)
          _       <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _       <- offers.upsertMasterServiceOffer(offer)
          _       <- masterLocations.upsertMasterLocation(location)
          _       <- variants.upsertMasterServiceOfferVariant(variant)
          _       <- db.execute("insert-unknown-enum-code-master-service-offer-variant-numeric-attribute") {
            sql"""insert into master_service_offer_variant_numeric_attributes (
                 |  master_service_offer_variant_id,
                 |  attribute_code,
                 |  value
                 |)
                 |values (
                 |  $variantId,
                 |  ${AttributeDefinition.HairRemovalMethodAttribute.code},
                 |  ${BigDecimal("999")}
                 |)
                 |on conflict (master_service_offer_variant_id, attribute_code) do update set
                 |  value = excluded.value
                 |""".stripMargin.update.run
          }
          result <- variants.getMasterServiceOfferVariant(variantId).either
          _      <- assertIO(
            result.left.exists {
              case QueryFailure.OperationFailure(operationName, message) =>
                operationName == "load-master-service-offer-variant-attributes" &&
                message.contains(AttributeDefinition.HairRemovalMethodAttribute.code) &&
                message.contains("unknown enum int code") &&
                message.contains("999")
              case _ =>
                false
            }
          )
        } yield ()
    }

    "reject non-integer numeric value for enum attribute definition" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        serviceVariantSchemas: ServiceVariantSchemas[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-non-integer-enum-category-$categoryId")
          master      = Master(masterId, s"numeric-non-integer-enum-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-non-integer-enum-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-non-integer-enum-location-$locationId",
            s"numeric-non-integer-enum-address-$locationId",
            BigDecimal("23.0000"),
            BigDecimal("24.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.HairRemovalMethodAttribute, false))
          variant <- makeVariant(variantId, offerId, locationId)
          _       <- categories.upsertCategory(category)
          _       <- masters.upsertMaster(master)
          _       <- services.upsertService(service)
          _       <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _       <- offers.upsertMasterServiceOffer(offer)
          _       <- masterLocations.upsertMasterLocation(location)
          _       <- variants.upsertMasterServiceOfferVariant(variant)
          _       <- db.execute("insert-non-integer-enum-master-service-offer-variant-numeric-attribute") {
            sql"""insert into master_service_offer_variant_numeric_attributes (
                 |  master_service_offer_variant_id,
                 |  attribute_code,
                 |  value
                 |)
                 |values (
                 |  $variantId,
                 |  ${AttributeDefinition.HairRemovalMethodAttribute.code},
                 |  ${BigDecimal("2.5")}
                 |)
                 |on conflict (master_service_offer_variant_id, attribute_code) do update set
                 |  value = excluded.value
                 |""".stripMargin.update.run
          }
          result <- variants.getMasterServiceOfferVariant(variantId).either
          _      <- assertIO(
            result.left.exists {
              case QueryFailure.OperationFailure(operationName, message) =>
                operationName == "load-master-service-offer-variant-attributes" &&
                message.contains(AttributeDefinition.HairRemovalMethodAttribute.code) &&
                message.contains("Int-compatible numeric value") &&
                message.contains("non-integer numeric value") &&
                message.contains("2.5")
              case _ =>
                false
            }
          )
        } yield ()
    }

    "reject unknown attribute code from numeric storage" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
        db: SQL[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"numeric-unknown-category-$categoryId")
          master      = Master(masterId, s"numeric-unknown-master-$masterId")
          service     = Service(serviceId, categoryId, s"numeric-unknown-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"numeric-unknown-location-$locationId",
            s"numeric-unknown-address-$locationId",
            BigDecimal("13.0000"),
            BigDecimal("14.0000"),
          )
          variant <- makeVariant(variantId, offerId, locationId)
          _       <- categories.upsertCategory(category)
          _       <- masters.upsertMaster(master)
          _       <- services.upsertService(service)
          _       <- offers.upsertMasterServiceOffer(offer)
          _       <- masterLocations.upsertMasterLocation(location)
          _       <- variants.upsertMasterServiceOfferVariant(variant)
          _       <- db.execute("insert-unknown-master-service-offer-variant-numeric-attribute") {
            sql"""insert into master_service_offer_variant_numeric_attributes (
                 |  master_service_offer_variant_id,
                 |  attribute_code,
                 |  value
                 |)
                 |values (
                 |  $variantId,
                 |  ${"unknown_attribute_code"},
                 |  ${BigDecimal("2.0")}
                 |)
                 |""".stripMargin.update.run
          }
          result <- variants.getMasterServiceOfferVariant(variantId).either
          _      <- assertIO(
            result.left.exists {
              case QueryFailure.OperationFailure(operationName, message) =>
                operationName == "load-master-service-offer-variant-attributes" &&
                message == "Unknown MasterServiceOfferVariant attribute code: unknown_attribute_code"
              case _ =>
                false
            }
          )
        } yield ()
    }

    "create only the numeric attribute table" in {
      (db: SQL[IO]) =>
        for {
          tableNames <- db.execute("list-master-service-offer-variant-attribute-tables") {
            sql"""select table_name
                               from information_schema.tables
                               where table_schema = 'public'
                                 and table_name like 'master_service_offer_variant_%_attributes'
                               order by table_name asc
                             """.query[String].to[List]
          }
          _ <- assertIO(
            tableNames == List("master_service_offer_variant_numeric_attributes")
          )
        } yield ()
    }
  }
}

class VariantAttributeStorageSpecPostgres extends VariantAttributeStorageSpec with ProdTest
class MasterServiceOfferVariantsStorageValidationTestPostgres extends MasterServiceOfferVariantsStorageValidationTest with ProdTest
