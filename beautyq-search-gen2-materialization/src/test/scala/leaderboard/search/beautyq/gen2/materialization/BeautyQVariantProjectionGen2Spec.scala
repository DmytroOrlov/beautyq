package leaderboard.search.beautyq.gen2.materialization

import leaderboard.model.*
import leaderboard.model.Category.CategoryId
import BeautyQVariantProjectionError.*
import leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2
import leaderboard.search.gen2.contract.{GeoPoint, SearchValueCodec}
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class BeautyQVariantProjectionGen2Spec extends AnyWordSpec {
  import BeautyQGen2MaterializationFixtures.*

  private def humanize(value: String): String = value.replace('_', ' ')

  private def normalizeText(parts: Iterable[String]): String =
    parts.iterator.map(_.trim).filter(_.nonEmpty).mkString(" ")

  private def canonicalDecimal(value: BigDecimal): String = SearchValueCodec.bigDecimal.encodeCanonical(value)

  private def remakeVariant(
    id: MasterServiceOfferVariantId                 = variantId,
    masterServiceOfferId: MasterServiceOfferId      = offerId,
    masterLocationId: MasterLocationId              = locationId,
    priceFrom: BigDecimal                           = variant.priceFrom,
    priceTo: BigDecimal                             = variant.priceTo,
    durationMin: Int                                = variant.durationMin,
    attributes: MasterServiceOfferVariantAttributes = variant.attributes,
  ): MasterServiceOfferVariant =
    MasterServiceOfferVariant
      .make(id, masterServiceOfferId, masterLocationId, priceFrom, priceTo, durationMin, attributes)
      .getOrElse(throw new IllegalStateException("test variant must be valid"))

  private def projectAndExpectErrors(broken: BeautyQSearchSnapshot): Vector[BeautyQVariantProjectionError] =
    BeautyQVariantProjectionGen2.project(broken) match {
      case Left(errors)     => errors.toVector
      case Right(documents) => fail(s"expected projection errors but got a successful result: $documents")
    }

  private def projectAndExpectSuccess(source: BeautyQSearchSnapshot): Vector[VariantSearchDocumentGen2] =
    BeautyQVariantProjectionGen2.project(source) match {
      case Right(documents) => documents
      case Left(errors)     => fail(s"expected a successful projection but got errors: ${errors.toVector}")
    }

  private val expectedServiceText = normalizeText(Vector(service.name, category.name))
  private val expectedAttributeTokens = Vector(
    "nail_coating_type",
    "gel_polish",
    humanize("nail_coating_type"),
    humanize("gel_polish"),
    "with_removal",
    humanize("with_removal"),
    "true",
    "session_count",
    humanize("session_count"),
    "3",
    "deposit_amount",
    humanize("deposit_amount"),
    canonicalDecimal(BigDecimal("15.5000")),
  )
  private val expectedAttributeText = normalizeText(expectedAttributeTokens)
  private val expectedProviderText = normalizeText(Vector(master.name, location.name))
  private val expectedLocationText = normalizeText(Vector(location.name, location.address, category.name))
  private val expectedAllText = normalizeText(Vector(expectedServiceText, expectedAttributeText, expectedProviderText, expectedLocationText))

  private val expectedDocument = VariantSearchDocumentGen2(
    variantId = variantId,
    masterServiceOfferId = offerId,
    masterLocationId = locationId,
    masterId = masterId,
    serviceId = serviceId,
    serviceCode = serviceCode,
    categoryId = categoryId,
    categoryCode = categoryCode,
    serviceName = "Fixture Service A",
    categoryName = "Fixture Category A",
    masterName = "Fixture Master A",
    locationName = "Fixture Location A",
    address = "1 Fixture Street",
    location = GeoPoint(BigDecimal("52.520000"), BigDecimal("13.405000")),
    lat = BigDecimal("52.520000"),
    lon = BigDecimal("13.405000"),
    priceFrom = BigDecimal("30.0000"),
    priceTo = BigDecimal("45.0000"),
    durationMin = 60,
    enumAttributes = Map("nail_coating_type" -> "gel_polish"),
    booleanAttributes = Map("with_removal" -> true),
    intAttributes = Map("session_count" -> 3),
    bigDecimalAttributes = Map("deposit_amount" -> BigDecimal("15.5000")),
    allText = expectedAllText,
    serviceText = expectedServiceText,
    attributeText = expectedAttributeText,
    providerText = expectedProviderText,
    locationText = expectedLocationText,
  )

  "BeautyQVariantProjectionGen2" should {

    "construct the exact expected document for a valid golden snapshot" in {
      assert(projectAndExpectSuccess(snapshot) == Vector(expectedDocument))
    }

    "resolve serviceCode/categoryCode from the joined Service/Category rows, not the variant itself" in {
      val documents = projectAndExpectSuccess(combinedSnapshot)
      val docA = documents.find(_.variantId == variantId).getOrElse(fail("variant A document missing"))
      val docB = documents.find(_.variantId == variantId2).getOrElse(fail("variant B document missing"))

      assert(docA.serviceCode == serviceCode)
      assert(docA.categoryCode == categoryCode)
      assert(docB.serviceCode == serviceCode2)
      assert(docB.categoryCode == categoryCode2)
      assert(docA.serviceCode != docB.serviceCode)
      assert(docA.categoryCode != docB.categoryCode)
    }

    "produce the exact typed GeoPoint" in {
      assert(projectAndExpectSuccess(snapshot).head.location == GeoPoint(BigDecimal("52.520000"), BigDecimal("13.405000")))
    }

    "produce the exact raw latitude and longitude" in {
      val document = projectAndExpectSuccess(snapshot).head
      assert(document.lat == BigDecimal("52.520000"))
      assert(document.lon == BigDecimal("13.405000"))
    }

    "produce the exact enum attribute map" in {
      assert(projectAndExpectSuccess(snapshot).head.enumAttributes == Map("nail_coating_type" -> "gel_polish"))
    }

    "produce the exact boolean attribute map" in {
      assert(projectAndExpectSuccess(snapshot).head.booleanAttributes == Map("with_removal" -> true))
    }

    "produce the exact integer attribute map" in {
      assert(projectAndExpectSuccess(snapshot).head.intAttributes == Map("session_count" -> 3))
    }

    "produce the exact decimal attribute map" in {
      assert(projectAndExpectSuccess(snapshot).head.bigDecimalAttributes == Map("deposit_amount" -> BigDecimal("15.5000")))
    }

    "order attribute tokens as enum, boolean, int, then bigDecimal families" in {
      assert(projectAndExpectSuccess(snapshot).head.attributeText == expectedAttributeText)
    }

    "compose the exact five text fields" in {
      val document = projectAndExpectSuccess(snapshot).head
      assert(document.serviceText == expectedServiceText)
      assert(document.attributeText == expectedAttributeText)
      assert(document.providerText == expectedProviderText)
      assert(document.locationText == expectedLocationText)
      assert(document.allText == expectedAllText)
    }

    "not let input variant order affect output order" in {
      val forward = projectAndExpectSuccess(combinedSnapshot)
      val backward = projectAndExpectSuccess(combinedSnapshot.copy(masterServiceOfferVariants = combinedSnapshot.masterServiceOfferVariants.reverse))

      assert(forward == backward)
    }

    "return documents sorted by canonical variant id" in {
      val documents = projectAndExpectSuccess(combinedSnapshot.copy(masterServiceOfferVariants = combinedSnapshot.masterServiceOfferVariants.reverse))
      assert(documents.map(_.variantId) == Vector(variantId, variantId2))
    }

    "report duplicate Category ids" in {
      val duplicate = category.copy(code = CategoryCode.unsafeFromString("fixture_category_a_dup"), name = "Duplicate Category Row")
      val errors = projectAndExpectErrors(snapshot.copy(categories = Vector(category, duplicate)))
      assert(errors == Vector(DuplicateEntityId("Category", categoryId.toString)))
    }

    "report duplicate Service ids" in {
      val duplicate = service.copy(code = ServiceCode.unsafeFromString("fixture_service_a_dup"), name = "Duplicate Service Row")
      val errors = projectAndExpectErrors(snapshot.copy(services = Vector(service, duplicate)))
      assert(errors == Vector(DuplicateEntityId("Service", serviceId.toString)))
    }

    "report duplicate Master ids" in {
      val duplicate = master.copy(name = "Duplicate Master Row")
      val errors = projectAndExpectErrors(snapshot.copy(masters = Vector(master, duplicate)))
      assert(errors == Vector(DuplicateEntityId("Master", masterId.toString)))
    }

    "report duplicate MasterLocation ids" in {
      val duplicate = location.copy(name = "Duplicate Location Row")
      val errors = projectAndExpectErrors(snapshot.copy(masterLocations = Vector(location, duplicate)))
      assert(errors == Vector(DuplicateEntityId("MasterLocation", locationId.toString)))
    }

    "report duplicate MasterServiceOffer ids" in {
      val duplicate = offer.copy(masterId = masterId)
      val errors = projectAndExpectErrors(snapshot.copy(masterServiceOffers = Vector(offer, duplicate)))
      assert(errors == Vector(DuplicateEntityId("MasterServiceOffer", offerId.toString)))
    }

    "report duplicate MasterServiceOfferVariant ids" in {
      val duplicate = remakeVariant(priceFrom = variant.priceFrom + BigDecimal("1.0"))
      val errors = projectAndExpectErrors(snapshot.copy(masterServiceOfferVariants = Vector(variant, duplicate)))
      assert(errors == Vector(DuplicateEntityId("MasterServiceOfferVariant", variantId.toString)))
    }

    "report duplicate category codes" in {
      val otherCategoryId = CategoryId(UUID.fromString("c0000000-0000-0000-0000-000000000001"))
      val sameCodeOtherCategory = Category(otherCategoryId, categoryCode, leaderboard.model.Category.rootCategoryId, 1, "Another Category")

      val errors = projectAndExpectErrors(snapshot.copy(categories = Vector(category, sameCodeOtherCategory)))
      assert(errors == Vector(DuplicateCategoryCode(categoryCode)))
    }

    "report duplicate service codes" in {
      val otherServiceId = ServiceId(UUID.fromString("c0000000-0000-0000-0000-000000000002"))
      val sameCodeOtherService = Service(otherServiceId, serviceCode, categoryId, "Another Service")

      val errors = projectAndExpectErrors(snapshot.copy(services = Vector(service, sameCodeOtherService)))
      assert(errors == Vector(DuplicateServiceCode(serviceCode)))
    }

    "report duplicate service schemas" in {
      val duplicateSchema = ServiceVariantSchema.fromItems(
        serviceId,
        Vector(
          ServiceVariantSchemaItem(AttributeDefinition.SessionCount, required = false),
          ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, required = false),
          ServiceVariantSchemaItem(AttributeDefinition.WithRemoval, required = false),
          ServiceVariantSchemaItem(AttributeDefinition.NailCoatingTypeAttribute, required = false),
          ServiceVariantSchemaItem(AttributeDefinition.MaxClients, required = false),
        ),
      )

      val errors = projectAndExpectErrors(snapshot.copy(serviceVariantSchemas = Vector(schema, duplicateSchema)))
      assert(errors == Vector(DuplicateServiceSchema(serviceId)))
    }

    "report a missing offer" in {
      val errors = projectAndExpectErrors(snapshot.copy(masterServiceOfferVariants = Vector(remakeVariant(masterServiceOfferId = offerId2))))
      assert(errors == Vector(MissingOffer(variantId, offerId2)))
    }

    "report a missing service" in {
      val offerMissingService = MasterServiceOffer(offerId, masterId, serviceId2)
      val errors = projectAndExpectErrors(snapshot.copy(masterServiceOffers = Vector(offerMissingService)))
      assert(errors == Vector(MissingService(variantId, serviceId2)))
    }

    "report a missing category" in {
      val serviceMissingCategory = service.copy(categoryId = categoryId2)
      val errors = projectAndExpectErrors(snapshot.copy(services = Vector(serviceMissingCategory)))
      assert(errors == Vector(MissingCategory(variantId, categoryId2)))
    }

    "report a missing master" in {
      val offerMissingMaster = offer.copy(masterId = masterId2)
      val locationSameMaster = location.copy(masterId = masterId2)
      val errors = projectAndExpectErrors(
        snapshot.copy(masterServiceOffers = Vector(offerMissingMaster), masterLocations = Vector(locationSameMaster))
      )
      assert(errors == Vector(MissingMaster(variantId, masterId2)))
    }

    "report a missing location" in {
      val errors = projectAndExpectErrors(snapshot.copy(masterServiceOfferVariants = Vector(remakeVariant(masterLocationId = locationId2))))
      assert(errors == Vector(MissingLocation(variantId, locationId2)))
    }

    "report a missing service schema" in {
      val errors = projectAndExpectErrors(snapshot.copy(serviceVariantSchemas = Vector.empty))
      assert(errors == Vector(MissingServiceSchema(variantId, serviceId)))
    }

    "report an offer/location master mismatch" in {
      val errors = projectAndExpectErrors(
        snapshot.copy(
          masterLocations = Vector(location, location2),
          masterServiceOfferVariants = Vector(remakeVariant(masterLocationId = locationId2)),
        )
      )
      assert(errors == Vector(OfferLocationMasterMismatch(variantId, masterId, masterId2)))
    }

    "turn a disallowed attribute into a SchemaViolation" in {
      val attributesWithDisallowed = variantAttributes.copy(intValues = variantAttributes.intValues.updated(AttributeDefinition.MaxClients, 5))
      val errors = projectAndExpectErrors(snapshot.copy(masterServiceOfferVariants = Vector(remakeVariant(attributes = attributesWithDisallowed))))
      assert(errors == Vector(SchemaViolation(variantId, serviceId, "disallowed attribute max_clients")))
    }

    "turn a missing required attribute into a SchemaViolation" in {
      val requiredSchema = ServiceVariantSchema.fromItems(serviceId, Vector(ServiceVariantSchemaItem(AttributeDefinition.MaxClients, required = true)))
      val emptyAttributesVariant = remakeVariant(attributes = MasterServiceOfferVariantAttributes.empty)

      val errors = projectAndExpectErrors(
        snapshot.copy(serviceVariantSchemas = Vector(requiredSchema), masterServiceOfferVariants = Vector(emptyAttributesVariant))
      )
      assert(errors == Vector(SchemaViolation(variantId, serviceId, "missing required attribute max_clients")))
    }

    "accumulate errors from different variants" in {
      val badOfferId = MasterServiceOfferId(UUID.fromString("c0000000-0000-0000-0000-000000000003"))
      val badLocationId = MasterLocationId(UUID.fromString("c0000000-0000-0000-0000-000000000004"))

      val variantAMissingOffer = remakeVariant(masterServiceOfferId = badOfferId)
      val variantBMissingLocation = MasterServiceOfferVariant
        .make(variantId2, offerId2, badLocationId, BigDecimal("10.0000"), BigDecimal("20.0000"), 30, MasterServiceOfferVariantAttributes.empty)
        .getOrElse(throw new IllegalStateException("test variant must be valid"))

      val broken = BeautyQSearchSnapshot(
        categories = Vector(category, category2),
        services = Vector(service, service2),
        serviceVariantSchemas = Vector(schema, schema2),
        masters = Vector(master, master2),
        masterLocations = Vector(location, location2),
        masterServiceOffers = Vector(offer, offer2),
        masterServiceOfferVariants = Vector(variantAMissingOffer, variantBMissingLocation),
      )

      val errors = projectAndExpectErrors(broken)
      assert(errors.toSet == Set(MissingOffer(variantId, badOfferId), MissingLocation(variantId2, badLocationId)))
      assert(errors.size == 2)
    }

    "accumulate multiple independent errors from a single variant" in {
      val serviceMissingCategory = service.copy(categoryId = categoryId2)
      val broken = snapshot.copy(
        services = Vector(serviceMissingCategory),
        masterServiceOfferVariants = Vector(remakeVariant(masterLocationId = locationId2)),
      )

      val errors = projectAndExpectErrors(broken)
      assert(errors == Vector(MissingCategory(variantId, categoryId2), MissingLocation(variantId, locationId2)))
    }

    "produce a deterministic error order across repeated runs" in {
      val serviceMissingCategory = service.copy(categoryId = categoryId2)
      val broken = snapshot.copy(
        services = Vector(serviceMissingCategory),
        masterServiceOfferVariants = Vector(remakeVariant(masterLocationId = locationId2)),
      )

      assert(projectAndExpectErrors(broken) == projectAndExpectErrors(broken))
    }

    "not change error output when reordering duplicate invalid rows" in {
      val duplicateA = category.copy(name = "Duplicate Row A")
      val duplicateB = category.copy(name = "Duplicate Row B")

      val forward = projectAndExpectErrors(snapshot.copy(categories = Vector(duplicateA, duplicateB)))
      val backward = projectAndExpectErrors(snapshot.copy(categories = Vector(duplicateB, duplicateA)))

      assert(forward == backward)
    }

    "return Left rather than throw for a broad set of invalid snapshots" in {
      val brokenSnapshots = Vector(
        snapshot.copy(categories = Vector.empty),
        snapshot.copy(services = Vector.empty),
        snapshot.copy(masters = Vector.empty),
        snapshot.copy(masterLocations = Vector.empty),
        snapshot.copy(masterServiceOffers = Vector.empty),
        snapshot.copy(serviceVariantSchemas = Vector.empty),
        snapshot.copy(masterServiceOfferVariants = Vector(variant, variant)),
      )

      brokenSnapshots.foreach {
        broken =>
          assert(BeautyQVariantProjectionGen2.project(broken).isLeft)
      }
    }
  }
}
