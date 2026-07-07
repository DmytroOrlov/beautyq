package leaderboard

import leaderboard.model.*
import leaderboard.model.Category.{CategoryId, rootCategoryId}
import leaderboard.repo.{Categories, MasterLocations, MasterServiceOfferVariants, MasterServiceOffers, Masters, ServiceVariantSchemas, Services}
import zio.{IO, ZIO}

// AI-NOTE: For distage testkit memoizationRoots, Activation, and BIO patterns used here, see docs/LOCAL_LLM_IZUMI_DISTAGE_BIO_REFERENCE.md

abstract class MasterServiceOfferVariantsSpec extends LeaderboardTest with VariantTestFixtures {
  "MasterServiceOfferVariants" should {
    "upsert & get" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
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
          category    = Category(categoryId, rootCategoryId, 0, s"offer-variant-category-$categoryId")
          master      = Master(masterId, s"offer-variant-master-$masterId")
          service     = Service(serviceId, categoryId, s"offer-variant-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"offer-variant-$locationId",
            s"offer-variant-address-$locationId",
            BigDecimal("12.3400"),
            BigDecimal("56.7800"),
          )
          variant <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
          _       <- categories.upsertCategory(category)
          _       <- masters.upsertMaster(master)
          _       <- services.upsertService(service)
          _       <- offers.upsertMasterServiceOffer(offer)
          _       <- masterLocations.upsertMasterLocation(location)
          _       <- variants.upsertMasterServiceOfferVariant(variant)
          res     <- variants.getMasterServiceOfferVariant(variant.id)
          _       <- assertIO(res.contains(variant))
        } yield ()
    }

    "upsert & get with required and optional additional attributes allowed by service schema" in {
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
          category    = Category(categoryId, rootCategoryId, 0, s"offer-variant-attrs-category-$categoryId")
          master      = Master(masterId, s"offer-variant-attrs-master-$masterId")
          service     = Service(serviceId, categoryId, s"offer-variant-attrs-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"offer-variant-attrs-$locationId",
            s"offer-variant-attrs-address-$locationId",
            BigDecimal("13.3400"),
            BigDecimal("57.7800"),
          )
          schema = makeSchema(
            serviceId,
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, true),
            ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false),
            ServiceVariantSchemaItem(AttributeDefinition.MaterialsSurcharge, false),
          )
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            BigDecimal("30.0000"),
            BigDecimal("45.0000"),
            60,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 5)),
            bigDecimalAttributes = AttributeMap.Impl(
              Map(
                AttributeDefinition.DepositAmount      -> BigDecimal("15.0000"),
                AttributeDefinition.MaterialsSurcharge -> BigDecimal("7.5000"),
              )
            ),
          )
          _   <- categories.upsertCategory(category)
          _   <- masters.upsertMaster(master)
          _   <- services.upsertService(service)
          _   <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _   <- offers.upsertMasterServiceOffer(offer)
          _   <- masterLocations.upsertMasterLocation(location)
          _   <- variants.upsertMasterServiceOfferVariant(variant)
          res <- variants.getMasterServiceOfferVariant(variant.id)
          _   <- assertIO(res.contains(variant))
        } yield ()
    }

    "reject creating a variant when offer does not exist" in {
      (rnd: Rnd[IO], masters: Masters[IO], masterLocations: MasterLocations[IO], variants: MasterServiceOfferVariants[IO]) =>
        for {
          masterId   <- rnd[MasterId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          master      = Master(masterId, s"missing-offer-master-$masterId")
          location    = MasterLocation(
            locationId,
            masterId,
            s"missing-offer-location-$locationId",
            s"missing-offer-address-$locationId",
            BigDecimal("1.1000"),
            BigDecimal("2.2000"),
          )
          _       <- masters.upsertMaster(master)
          _       <- masterLocations.upsertMasterLocation(location)
          variant <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
          result  <- variants.upsertMasterServiceOfferVariant(variant).either
          _       <- assertIO(result.isLeft)
        } yield ()
    }

    "reject creating a variant when location does not exist" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
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
          category    = Category(categoryId, rootCategoryId, 0, s"missing-location-category-$categoryId")
          master      = Master(masterId, s"missing-location-master-$masterId")
          service     = Service(serviceId, categoryId, s"missing-location-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          _          <- categories.upsertCategory(category)
          _          <- masters.upsertMaster(master)
          _          <- services.upsertService(service)
          _          <- offers.upsertMasterServiceOffer(offer)
          variant    <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
          result     <- variants.upsertMasterServiceOfferVariant(variant).either
          _          <- assertIO(result.isLeft)
        } yield ()
    }

    "make rejects negative priceFrom" in {
      (rnd: Rnd[IO]) =>
        for {
          variantId  <- rnd[MasterServiceOfferVariantId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          result      = MasterServiceOfferVariant.make(variantId, offerId, locationId, BigDecimal("-1.0000"), BigDecimal("10.0000"), 60)
          _          <- assertIO(result == Left(MasterServiceOfferVariantValidationError.NegativePriceFrom(BigDecimal("-1.0000"))))
        } yield ()
    }

    "make rejects priceTo less than priceFrom" in {
      (rnd: Rnd[IO]) =>
        for {
          variantId  <- rnd[MasterServiceOfferVariantId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          result      = MasterServiceOfferVariant.make(variantId, offerId, locationId, BigDecimal("20.0000"), BigDecimal("10.0000"), 60)
          _          <- assertIO(
            result == Left(
              MasterServiceOfferVariantValidationError.PriceToLessThanPriceFrom(
                BigDecimal("20.0000"),
                BigDecimal("10.0000"),
              )
            )
          )
        } yield ()
    }

    "make rejects non-positive durationMin" in {
      (rnd: Rnd[IO]) =>
        for {
          variantId  <- rnd[MasterServiceOfferVariantId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          result      = MasterServiceOfferVariant.make(variantId, offerId, locationId, BigDecimal("20.0000"), BigDecimal("30.0000"), 0)
          _          <- assertIO(result == Left(MasterServiceOfferVariantValidationError.NonPositiveDurationMin(0)))
        } yield ()
    }

    "upsert rejects disallowed additional attribute by service schema" in {
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
          category    = Category(categoryId, rootCategoryId, 0, s"variant-schema-category-$categoryId")
          master      = Master(masterId, s"variant-schema-master-$masterId")
          service     = Service(serviceId, categoryId, s"variant-schema-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"variant-schema-location-$locationId",
            s"variant-schema-address-$locationId",
            BigDecimal("10.0000"),
            BigDecimal("20.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false))
          variant <- makeVariant(
            variantId,
            offerId,
            locationId,
            BigDecimal("30.0000"),
            BigDecimal("45.0000"),
            60,
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("8.0000"))),
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
                message == s"Service $serviceId does not allow MasterServiceOfferVariant attribute deposit_amount"
              case _ =>
                false
            }
          )
        } yield ()
    }

    "upsert rejects missing required additional attribute by service schema" in {
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
          category    = Category(categoryId, rootCategoryId, 0, s"variant-required-category-$categoryId")
          master      = Master(masterId, s"variant-required-master-$masterId")
          service     = Service(serviceId, categoryId, s"variant-required-service-$serviceId")
          offer       = MasterServiceOffer(offerId, masterId, serviceId)
          location    = MasterLocation(
            locationId,
            masterId,
            s"variant-required-location-$locationId",
            s"variant-required-address-$locationId",
            BigDecimal("10.0000"),
            BigDecimal("20.0000"),
          )
          schema   = makeSchema(serviceId, ServiceVariantSchemaItem(AttributeDefinition.SessionCount, true))
          variant <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
          _       <- categories.upsertCategory(category)
          _       <- masters.upsertMaster(master)
          _       <- services.upsertService(service)
          _       <- serviceVariantSchemas.upsertServiceVariantSchema(schema)
          _       <- offers.upsertMasterServiceOffer(offer)
          _       <- masterLocations.upsertMasterLocation(location)
          result  <- variants.upsertMasterServiceOfferVariant(variant).either
          _       <- assertIO(
            result.left.exists {
              case QueryFailure.OperationFailure(operationName, message) =>
                operationName == "upsert-master-service-offer-variant" &&
                message == s"Service $serviceId requires MasterServiceOfferVariant attribute session_count"
              case _ =>
                false
            }
          )
        } yield ()
    }

    "reject creating a variant when offer and location belong to different masters" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          master1Id  <- rnd[MasterId]
          master2Id  <- rnd[MasterId]
          serviceId  <- rnd[ServiceId]
          offerId    <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variantId  <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"mismatch-category-$categoryId")
          master1     = Master(master1Id, s"mismatch-master-a-$master1Id")
          master2     = Master(master2Id, s"mismatch-master-b-$master2Id")
          service     = Service(serviceId, categoryId, s"mismatch-service-$serviceId")
          offer       = MasterServiceOffer(offerId, master1Id, serviceId)
          location    = MasterLocation(
            locationId,
            master2Id,
            s"mismatch-location-$locationId",
            s"mismatch-address-$locationId",
            BigDecimal("17.0000"),
            BigDecimal("27.0000"),
          )
          result <- {
            for {
              _       <- categories.upsertCategory(category)
              _       <- masters.upsertMaster(master1)
              _       <- masters.upsertMaster(master2)
              _       <- services.upsertService(service)
              _       <- offers.upsertMasterServiceOffer(offer)
              _       <- masterLocations.upsertMasterLocation(location)
              variant <- makeVariant(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60)
              r       <- variants.upsertMasterServiceOfferVariant(variant).either
            } yield r
          }
          _ <- assertIO(result.isLeft)
        } yield ()
    }

    "allow creating several variants for one offer" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
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
          category     = Category(categoryId, rootCategoryId, 0, s"variants-by-offer-category-$categoryId")
          master       = Master(masterId, s"variants-by-offer-master-$masterId")
          service      = Service(serviceId, categoryId, s"variants-by-offer-service-$serviceId")
          offer        = MasterServiceOffer(offerId, masterId, serviceId)
          location1    = MasterLocation(
            location1Id,
            masterId,
            s"variants-location-a-$location1Id",
            s"variants-address-a-$location1Id",
            BigDecimal("10.0000"),
            BigDecimal("20.0000"),
          )
          location2 = MasterLocation(
            location2Id,
            masterId,
            s"variants-location-b-$location2Id",
            s"variants-address-b-$location2Id",
            BigDecimal("30.0000"),
            BigDecimal("40.0000"),
          )
          variant1 <- makeVariant(variant1Id, offerId, location1Id, BigDecimal("10.0000"), BigDecimal("20.0000"), 30)
          variant2 <- makeVariant(variant2Id, offerId, location2Id, BigDecimal("30.0000"), BigDecimal("40.0000"), 90)
          _        <- categories.upsertCategory(category)
          _        <- masters.upsertMaster(master)
          _        <- services.upsertService(service)
          _        <- offers.upsertMasterServiceOffer(offer)
          _        <- masterLocations.upsertMasterLocation(location1)
          _        <- masterLocations.upsertMasterLocation(location2)
          _        <- variants.upsertMasterServiceOfferVariant(variant1)
          _        <- variants.upsertMasterServiceOfferVariant(variant2)
          res      <- variants.getMasterServiceOfferVariantsByOffer(offerId)
          _        <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "allow creating several variants for one location" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          service1Id <- rnd[ServiceId]
          service2Id <- rnd[ServiceId]
          offer1Id   <- rnd[MasterServiceOfferId]
          offer2Id   <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          variant1Id <- rnd[MasterServiceOfferVariantId]
          variant2Id <- rnd[MasterServiceOfferVariantId]
          category    = Category(categoryId, rootCategoryId, 0, s"variants-by-location-category-$categoryId")
          master      = Master(masterId, s"variants-by-location-master-$masterId")
          service1    = Service(service1Id, categoryId, s"variants-by-location-service-a-$service1Id")
          service2    = Service(service2Id, categoryId, s"variants-by-location-service-b-$service2Id")
          offer1      = MasterServiceOffer(offer1Id, masterId, service1Id)
          offer2      = MasterServiceOffer(offer2Id, masterId, service2Id)
          location    = MasterLocation(
            locationId,
            masterId,
            s"variants-shared-location-$locationId",
            s"variants-shared-address-$locationId",
            BigDecimal("50.0000"),
            BigDecimal("60.0000"),
          )
          variant1 <- makeVariant(variant1Id, offer1Id, locationId, BigDecimal("15.0000"), BigDecimal("25.0000"), 30)
          variant2 <- makeVariant(variant2Id, offer2Id, locationId, BigDecimal("35.0000"), BigDecimal("45.0000"), 90)
          _        <- categories.upsertCategory(category)
          _        <- masters.upsertMaster(master)
          _        <- services.upsertService(service1)
          _        <- services.upsertService(service2)
          _        <- offers.upsertMasterServiceOffer(offer1)
          _        <- offers.upsertMasterServiceOffer(offer2)
          _        <- masterLocations.upsertMasterLocation(location)
          _        <- variants.upsertMasterServiceOfferVariant(variant1)
          _        <- variants.upsertMasterServiceOfferVariant(variant2)
          res      <- variants.getMasterServiceOfferVariantsByLocation(locationId)
          _        <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "return only variants of the requested offer" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId  <- rnd[CategoryId]
          masterId    <- rnd[MasterId]
          service1Id  <- rnd[ServiceId]
          service2Id  <- rnd[ServiceId]
          offer1Id    <- rnd[MasterServiceOfferId]
          offer2Id    <- rnd[MasterServiceOfferId]
          location1Id <- rnd[MasterLocationId]
          location2Id <- rnd[MasterLocationId]
          otherLocId  <- rnd[MasterLocationId]
          variant1Id  <- rnd[MasterServiceOfferVariantId]
          variant2Id  <- rnd[MasterServiceOfferVariantId]
          otherId     <- rnd[MasterServiceOfferVariantId]
          category     = Category(categoryId, rootCategoryId, 0, s"variants-filter-offer-category-$categoryId")
          master       = Master(masterId, s"variants-filter-offer-master-$masterId")
          service1     = Service(service1Id, categoryId, s"variants-filter-offer-service-a-$service1Id")
          service2     = Service(service2Id, categoryId, s"variants-filter-offer-service-b-$service2Id")
          offer1       = MasterServiceOffer(offer1Id, masterId, service1Id)
          offer2       = MasterServiceOffer(offer2Id, masterId, service2Id)
          location1    = MasterLocation(
            location1Id,
            masterId,
            s"variants-filter-offer-location-a-$location1Id",
            s"variants-filter-offer-address-a-$location1Id",
            BigDecimal("11.0000"),
            BigDecimal("21.0000"),
          )
          location2 = MasterLocation(
            location2Id,
            masterId,
            s"variants-filter-offer-location-b-$location2Id",
            s"variants-filter-offer-address-b-$location2Id",
            BigDecimal("31.0000"),
            BigDecimal("41.0000"),
          )
          otherLoc = MasterLocation(
            otherLocId,
            masterId,
            s"variants-filter-offer-location-c-$otherLocId",
            s"variants-filter-offer-address-c-$otherLocId",
            BigDecimal("51.0000"),
            BigDecimal("61.0000"),
          )
          variant1 <- makeVariant(variant1Id, offer1Id, location1Id, BigDecimal("11.0000"), BigDecimal("21.0000"), 30)
          variant2 <- makeVariant(variant2Id, offer1Id, location2Id, BigDecimal("31.0000"), BigDecimal("41.0000"), 60)
          other    <- makeVariant(otherId, offer2Id, otherLocId, BigDecimal("51.0000"), BigDecimal("61.0000"), 90)
          _        <- categories.upsertCategory(category)
          _        <- masters.upsertMaster(master)
          _        <- services.upsertService(service1)
          _        <- services.upsertService(service2)
          _        <- offers.upsertMasterServiceOffer(offer1)
          _        <- offers.upsertMasterServiceOffer(offer2)
          _        <- masterLocations.upsertMasterLocation(location1)
          _        <- masterLocations.upsertMasterLocation(location2)
          _        <- masterLocations.upsertMasterLocation(otherLoc)
          _        <- variants.upsertMasterServiceOfferVariant(variant1)
          _        <- variants.upsertMasterServiceOfferVariant(variant2)
          _        <- variants.upsertMasterServiceOfferVariant(other)
          res      <- variants.getMasterServiceOfferVariantsByOffer(offer1Id)
          _        <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "return only variants of the requested location" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId  <- rnd[CategoryId]
          masterId    <- rnd[MasterId]
          service1Id  <- rnd[ServiceId]
          service2Id  <- rnd[ServiceId]
          offer1Id    <- rnd[MasterServiceOfferId]
          offer2Id    <- rnd[MasterServiceOfferId]
          location1Id <- rnd[MasterLocationId]
          location2Id <- rnd[MasterLocationId]
          variant1Id  <- rnd[MasterServiceOfferVariantId]
          variant2Id  <- rnd[MasterServiceOfferVariantId]
          otherId     <- rnd[MasterServiceOfferVariantId]
          category     = Category(categoryId, rootCategoryId, 0, s"variants-filter-location-category-$categoryId")
          master       = Master(masterId, s"variants-filter-location-master-$masterId")
          service1     = Service(service1Id, categoryId, s"variants-filter-location-service-a-$service1Id")
          service2     = Service(service2Id, categoryId, s"variants-filter-location-service-b-$service2Id")
          offer1       = MasterServiceOffer(offer1Id, masterId, service1Id)
          offer2       = MasterServiceOffer(offer2Id, masterId, service2Id)
          location1    = MasterLocation(
            location1Id,
            masterId,
            s"variants-filter-location-a-$location1Id",
            s"variants-filter-location-address-a-$location1Id",
            BigDecimal("13.0000"),
            BigDecimal("23.0000"),
          )
          location2 = MasterLocation(
            location2Id,
            masterId,
            s"variants-filter-location-b-$location2Id",
            s"variants-filter-location-address-b-$location2Id",
            BigDecimal("33.0000"),
            BigDecimal("43.0000"),
          )
          variant1 <- makeVariant(variant1Id, offer1Id, location1Id, BigDecimal("13.0000"), BigDecimal("23.0000"), 30)
          variant2 <- makeVariant(variant2Id, offer2Id, location1Id, BigDecimal("33.0000"), BigDecimal("43.0000"), 60)
          other    <- makeVariant(otherId, offer1Id, location2Id, BigDecimal("53.0000"), BigDecimal("63.0000"), 90)
          _        <- categories.upsertCategory(category)
          _        <- masters.upsertMaster(master)
          _        <- services.upsertService(service1)
          _        <- services.upsertService(service2)
          _        <- offers.upsertMasterServiceOffer(offer1)
          _        <- offers.upsertMasterServiceOffer(offer2)
          _        <- masterLocations.upsertMasterLocation(location1)
          _        <- masterLocations.upsertMasterLocation(location2)
          _        <- variants.upsertMasterServiceOfferVariant(variant1)
          _        <- variants.upsertMasterServiceOfferVariant(variant2)
          _        <- variants.upsertMasterServiceOfferVariant(other)
          res      <- variants.getMasterServiceOfferVariantsByLocation(location1Id)
          _        <- assertIO(res.toSet == Set(variant1, variant2))
        } yield ()
    }

    "return variants by offer sorted by id asc" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
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
          location3Id <- rnd[MasterLocationId]
          category     = Category(categoryId, rootCategoryId, 0, s"variants-sort-offer-category-$categoryId")
          master       = Master(masterId, s"variants-sort-offer-master-$masterId")
          service      = Service(serviceId, categoryId, s"variants-sort-offer-service-$serviceId")
          offer        = MasterServiceOffer(offerId, masterId, serviceId)
          location1    = MasterLocation(
            location1Id,
            masterId,
            s"variants-sort-offer-location-a-$location1Id",
            s"variants-sort-offer-address-a-$location1Id",
            BigDecimal("14.0000"),
            BigDecimal("24.0000"),
          )
          location2 = MasterLocation(
            location2Id,
            masterId,
            s"variants-sort-offer-location-b-$location2Id",
            s"variants-sort-offer-address-b-$location2Id",
            BigDecimal("34.0000"),
            BigDecimal("44.0000"),
          )
          location3 = MasterLocation(
            location3Id,
            masterId,
            s"variants-sort-offer-location-c-$location3Id",
            s"variants-sort-offer-address-c-$location3Id",
            BigDecimal("54.0000"),
            BigDecimal("64.0000"),
          )
          id1       = java.util.UUID.fromString("30000000-0000-0000-0000-000000000002")
          id2       = java.util.UUID.fromString("30000000-0000-0000-0000-000000000001")
          id3       = java.util.UUID.fromString("30000000-0000-0000-0000-000000000003")
          variant1 <- makeVariant(id1, offerId, location1Id, BigDecimal("14.0000"), BigDecimal("24.0000"), 30)
          variant2 <- makeVariant(id2, offerId, location2Id, BigDecimal("34.0000"), BigDecimal("44.0000"), 60)
          variant3 <- makeVariant(id3, offerId, location3Id, BigDecimal("54.0000"), BigDecimal("64.0000"), 90)
          _        <- categories.upsertCategory(category)
          _        <- masters.upsertMaster(master)
          _        <- services.upsertService(service)
          _        <- offers.upsertMasterServiceOffer(offer)
          _        <- masterLocations.upsertMasterLocation(location1)
          _        <- masterLocations.upsertMasterLocation(location2)
          _        <- masterLocations.upsertMasterLocation(location3)
          _        <- variants.upsertMasterServiceOfferVariant(variant1)
          _        <- variants.upsertMasterServiceOfferVariant(variant2)
          _        <- variants.upsertMasterServiceOfferVariant(variant3)
          res      <- variants.getMasterServiceOfferVariantsByOffer(offerId)
          _        <- assertIO(res == List(variant2, variant1, variant3))
        } yield ()
    }

    "return variants by location sorted by id asc" in {
      (rnd: Rnd[IO],
        categories: Categories[IO],
        masters: Masters[IO],
        services: Services[IO],
        masterLocations: MasterLocations[IO],
        offers: MasterServiceOffers[IO],
        variants: MasterServiceOfferVariants[IO],
      ) =>
        for {
          categoryId <- rnd[CategoryId]
          masterId   <- rnd[MasterId]
          service1Id <- rnd[ServiceId]
          service2Id <- rnd[ServiceId]
          service3Id <- rnd[ServiceId]
          offer1Id   <- rnd[MasterServiceOfferId]
          offer2Id   <- rnd[MasterServiceOfferId]
          offer3Id   <- rnd[MasterServiceOfferId]
          locationId <- rnd[MasterLocationId]
          category    = Category(categoryId, rootCategoryId, 0, s"variants-sort-location-category-$categoryId")
          master      = Master(masterId, s"variants-sort-location-master-$masterId")
          service1    = Service(service1Id, categoryId, s"variants-sort-location-service-a-$service1Id")
          service2    = Service(service2Id, categoryId, s"variants-sort-location-service-b-$service2Id")
          service3    = Service(service3Id, categoryId, s"variants-sort-location-service-c-$service3Id")
          offer1      = MasterServiceOffer(offer1Id, masterId, service1Id)
          offer2      = MasterServiceOffer(offer2Id, masterId, service2Id)
          offer3      = MasterServiceOffer(offer3Id, masterId, service3Id)
          location    = MasterLocation(
            locationId,
            masterId,
            s"variants-sort-location-$locationId",
            s"variants-sort-location-address-$locationId",
            BigDecimal("15.0000"),
            BigDecimal("25.0000"),
          )
          id1       = java.util.UUID.fromString("40000000-0000-0000-0000-000000000002")
          id2       = java.util.UUID.fromString("40000000-0000-0000-0000-000000000001")
          id3       = java.util.UUID.fromString("40000000-0000-0000-0000-000000000003")
          variant1 <- makeVariant(id1, offer1Id, locationId, BigDecimal("15.0000"), BigDecimal("25.0000"), 30)
          variant2 <- makeVariant(id2, offer2Id, locationId, BigDecimal("35.0000"), BigDecimal("45.0000"), 60)
          variant3 <- makeVariant(id3, offer3Id, locationId, BigDecimal("55.0000"), BigDecimal("65.0000"), 90)
          _        <- categories.upsertCategory(category)
          _        <- masters.upsertMaster(master)
          _        <- services.upsertService(service1)
          _        <- services.upsertService(service2)
          _        <- services.upsertService(service3)
          _        <- offers.upsertMasterServiceOffer(offer1)
          _        <- offers.upsertMasterServiceOffer(offer2)
          _        <- offers.upsertMasterServiceOffer(offer3)
          _        <- masterLocations.upsertMasterLocation(location)
          _        <- variants.upsertMasterServiceOfferVariant(variant1)
          _        <- variants.upsertMasterServiceOfferVariant(variant2)
          _        <- variants.upsertMasterServiceOfferVariant(variant3)
          res      <- variants.getMasterServiceOfferVariantsByLocation(locationId)
          _        <- assertIO(res == List(variant2, variant1, variant3))
        } yield ()
    }

    "upsert overwrites existing variant with same id and replaces additional attributes" in {
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
          service1Id  <- rnd[ServiceId]
          service2Id  <- rnd[ServiceId]
          offer1Id    <- rnd[MasterServiceOfferId]
          offer2Id    <- rnd[MasterServiceOfferId]
          location1Id <- rnd[MasterLocationId]
          location2Id <- rnd[MasterLocationId]
          variantId   <- rnd[MasterServiceOfferVariantId]
          category     = Category(categoryId, rootCategoryId, 0, s"variants-overwrite-category-$categoryId")
          master       = Master(masterId, s"variants-overwrite-master-$masterId")
          service1     = Service(service1Id, categoryId, s"variants-overwrite-service-a-$service1Id")
          service2     = Service(service2Id, categoryId, s"variants-overwrite-service-b-$service2Id")
          offer1       = MasterServiceOffer(offer1Id, masterId, service1Id)
          offer2       = MasterServiceOffer(offer2Id, masterId, service2Id)
          location1    = MasterLocation(
            location1Id,
            masterId,
            s"variants-overwrite-location-a-$location1Id",
            s"variants-overwrite-address-a-$location1Id",
            BigDecimal("16.0000"),
            BigDecimal("26.0000"),
          )
          location2 = MasterLocation(
            location2Id,
            masterId,
            s"variants-overwrite-location-b-$location2Id",
            s"variants-overwrite-address-b-$location2Id",
            BigDecimal("36.0000"),
            BigDecimal("46.0000"),
          )
          initialSchema = makeSchema(
            service1Id,
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false),
            ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, false),
          )
          updatedSchema = makeSchema(
            service2Id,
            ServiceVariantSchemaItem(AttributeDefinition.MaterialsSurcharge, false),
            ServiceVariantSchemaItem(AttributeDefinition.SessionCount, false),
          )
          initial <- makeVariant(
            variantId,
            offer1Id,
            location1Id,
            BigDecimal("16.0000"),
            BigDecimal("26.0000"),
            30,
            intAttributes        = AttributeMap.Impl(Map(AttributeDefinition.SessionCount -> 1)),
            bigDecimalAttributes = AttributeMap.Impl(Map(AttributeDefinition.DepositAmount -> BigDecimal("10.0000"))),
          )
          updated <- makeVariant(
            variantId,
            offer2Id,
            location2Id,
            BigDecimal("36.0000"),
            BigDecimal("46.0000"),
            90,
            bigDecimalAttributes = AttributeMap.Impl(
              Map(
                AttributeDefinition.MaterialsSurcharge -> BigDecimal("12.0000")
              )
            ),
          )
          _   <- categories.upsertCategory(category)
          _   <- masters.upsertMaster(master)
          _   <- services.upsertService(service1)
          _   <- services.upsertService(service2)
          _   <- serviceVariantSchemas.upsertServiceVariantSchema(initialSchema)
          _   <- serviceVariantSchemas.upsertServiceVariantSchema(updatedSchema)
          _   <- offers.upsertMasterServiceOffer(offer1)
          _   <- offers.upsertMasterServiceOffer(offer2)
          _   <- masterLocations.upsertMasterLocation(location1)
          _   <- masterLocations.upsertMasterLocation(location2)
          _   <- variants.upsertMasterServiceOfferVariant(initial)
          _   <- variants.upsertMasterServiceOfferVariant(updated)
          res <- variants.getMasterServiceOfferVariant(variantId)
          _   <- assertIO(res.contains(updated))
        } yield ()
    }

  }

}

class MasterServiceOfferVariantsSpecDummy extends MasterServiceOfferVariantsSpec with DummyTest
class MasterServiceOfferVariantsSpecPostgres extends MasterServiceOfferVariantsSpec with ProdTest
