package leaderboard.search.beautyq.gen2.materialization

import leaderboard.model.*
import leaderboard.model.Category.CategoryId

import java.util.UUID

/** Fixed-ID, immutable test data for the Gen2 materialization pure test suites: one complete, mutually
  * consistent "golden" BeautyQ graph (category -> service -> schema, master -> location -> offer ->
  * variant) plus one independent second graph, so tests can compose valid/invalid snapshots by
  * including, omitting, or `.copy`-ing these fixed rows without depending on random or current-time
  * values.
  */
private[materialization] object BeautyQGen2MaterializationFixtures {

  private def uuid(literal: String): UUID = UUID.fromString(literal)

  // ---- Graph A: the golden, fully valid graph -----------------------------------------------------

  val categoryId: CategoryId = CategoryId(uuid("a0000000-0000-0000-0000-000000000001"))
  val categoryCode: CategoryCode = CategoryCode.unsafeFromString("fixture_category_a")
  val category: Category = Category(categoryId, categoryCode, Category.rootCategoryId, 1, "Fixture Category A")

  val serviceId: ServiceId = ServiceId(uuid("a0000000-0000-0000-0000-000000000002"))
  val serviceCode: ServiceCode = ServiceCode.unsafeFromString("fixture_service_a")
  val service: Service = Service(serviceId, serviceCode, categoryId, "Fixture Service A")

  val schema: ServiceVariantSchema =
    ServiceVariantSchema.fromItems(
      serviceId,
      Vector(
        ServiceVariantSchemaItem(AttributeDefinition.SessionCount, required = false),
        ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, required = false),
        ServiceVariantSchemaItem(AttributeDefinition.WithRemoval, required = false),
        ServiceVariantSchemaItem(AttributeDefinition.NailCoatingTypeAttribute, required = false),
      ),
    )

  val masterId: MasterId = MasterId(uuid("a0000000-0000-0000-0000-000000000003"))
  val master: Master = Master(masterId, "Fixture Master A")

  val locationId: MasterLocationId = MasterLocationId(uuid("a0000000-0000-0000-0000-000000000004"))
  val location: MasterLocation =
    MasterLocation(locationId, masterId, "Fixture Location A", "1 Fixture Street", BigDecimal("52.520000"), BigDecimal("13.405000"))

  val offerId: MasterServiceOfferId = MasterServiceOfferId(uuid("a0000000-0000-0000-0000-000000000005"))
  val offer: MasterServiceOffer = MasterServiceOffer(offerId, masterId, serviceId)

  val variantId: MasterServiceOfferVariantId = MasterServiceOfferVariantId(uuid("a0000000-0000-0000-0000-000000000006"))

  val variantAttributes: MasterServiceOfferVariantAttributes =
    MasterServiceOfferVariantAttributes(
      intValues = AttributeMap.empty.updated(AttributeDefinition.SessionCount, 3),
      bigDecimalValues = AttributeMap.empty.updated(AttributeDefinition.DepositAmount, BigDecimal("15.5000")),
      enumValues = AttributeMap.empty.updated(AttributeDefinition.NailCoatingTypeAttribute, NailCoatingType.GelPolish),
      booleanValues = AttributeMap.empty.updated(AttributeDefinition.WithRemoval, true),
    )

  val variant: MasterServiceOfferVariant =
    MasterServiceOfferVariant
      .make(variantId, offerId, locationId, BigDecimal("30.0000"), BigDecimal("45.0000"), 60, variantAttributes)
      .getOrElse(throw new IllegalStateException("fixture variant must be valid"))

  val snapshot: BeautyQSearchSnapshot =
    BeautyQSearchSnapshot(
      categories = Vector(category),
      services = Vector(service),
      serviceVariantSchemas = Vector(schema),
      masters = Vector(master),
      masterLocations = Vector(location),
      masterServiceOffers = Vector(offer),
      masterServiceOfferVariants = Vector(variant),
    )

  // ---- Graph B: a second, independent, fully valid graph (distinct ids/codes) -----------------------

  val categoryId2: CategoryId = CategoryId(uuid("b0000000-0000-0000-0000-000000000001"))
  val categoryCode2: CategoryCode = CategoryCode.unsafeFromString("fixture_category_b")
  val category2: Category = Category(categoryId2, categoryCode2, Category.rootCategoryId, 1, "Fixture Category B")

  val serviceId2: ServiceId = ServiceId(uuid("b0000000-0000-0000-0000-000000000002"))
  val serviceCode2: ServiceCode = ServiceCode.unsafeFromString("fixture_service_b")
  val service2: Service = Service(serviceId2, serviceCode2, categoryId2, "Fixture Service B")

  val schema2: ServiceVariantSchema = ServiceVariantSchema.empty(serviceId2)

  val masterId2: MasterId = MasterId(uuid("b0000000-0000-0000-0000-000000000003"))
  val master2: Master = Master(masterId2, "Fixture Master B")

  val locationId2: MasterLocationId = MasterLocationId(uuid("b0000000-0000-0000-0000-000000000004"))
  val location2: MasterLocation =
    MasterLocation(locationId2, masterId2, "Fixture Location B", "2 Fixture Street", BigDecimal("48.856600"), BigDecimal("2.352200"))

  val offerId2: MasterServiceOfferId = MasterServiceOfferId(uuid("b0000000-0000-0000-0000-000000000005"))
  val offer2: MasterServiceOffer = MasterServiceOffer(offerId2, masterId2, serviceId2)

  val variantId2: MasterServiceOfferVariantId = MasterServiceOfferVariantId(uuid("b0000000-0000-0000-0000-000000000006"))

  val variant2: MasterServiceOfferVariant =
    MasterServiceOfferVariant
      .make(variantId2, offerId2, locationId2, BigDecimal("10.0000"), BigDecimal("20.0000"), 30, MasterServiceOfferVariantAttributes.empty)
      .getOrElse(throw new IllegalStateException("fixture variant2 must be valid"))

  val snapshot2: BeautyQSearchSnapshot =
    BeautyQSearchSnapshot(
      categories = Vector(category2),
      services = Vector(service2),
      serviceVariantSchemas = Vector(schema2),
      masters = Vector(master2),
      masterLocations = Vector(location2),
      masterServiceOffers = Vector(offer2),
      masterServiceOfferVariants = Vector(variant2),
    )

  val combinedSnapshot: BeautyQSearchSnapshot =
    BeautyQSearchSnapshot(
      categories = snapshot.categories ++ snapshot2.categories,
      services = snapshot.services ++ snapshot2.services,
      serviceVariantSchemas = snapshot.serviceVariantSchemas ++ snapshot2.serviceVariantSchemas,
      masters = snapshot.masters ++ snapshot2.masters,
      masterLocations = snapshot.masterLocations ++ snapshot2.masterLocations,
      masterServiceOffers = snapshot.masterServiceOffers ++ snapshot2.masterServiceOffers,
      masterServiceOfferVariants = snapshot.masterServiceOfferVariants ++ snapshot2.masterServiceOfferVariants,
    )
}
