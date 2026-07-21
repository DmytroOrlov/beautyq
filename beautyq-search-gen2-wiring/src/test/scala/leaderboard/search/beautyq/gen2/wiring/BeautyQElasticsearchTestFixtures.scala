package leaderboard.search.beautyq.gen2.wiring

import leaderboard.model.*
import leaderboard.model.Category.CategoryId
import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.beautyq.gen2.materialization.{BeautyQSearchSnapshot, MaterializedBeautyQVariantDocuments}
import leaderboard.search.gen2.contract.GeoPoint
import leaderboard.search.gen2.core.materialization.*

import java.time.Instant
import java.util.UUID

object BeautyQElasticsearchTestFixtures {
  val document: VariantSearchDocumentGen2 =
    VariantSearchDocumentGen2(
      variantId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
      masterServiceOfferId = MasterServiceOfferId(UUID.fromString("00000000-0000-0000-0000-000000000002")),
      masterLocationId = MasterLocationId(UUID.fromString("00000000-0000-0000-0000-000000000003")),
      masterId = MasterId(UUID.fromString("00000000-0000-0000-0000-000000000004")),
      serviceId = ServiceId(UUID.fromString("00000000-0000-0000-0000-000000000005")),
      serviceCode = ServiceCode.fromString("manicure").fold(error => throw new IllegalArgumentException(error.toString), identity),
      categoryId = CategoryId(UUID.fromString("00000000-0000-0000-0000-000000000006")),
      categoryCode = CategoryCode.fromString("nails").fold(error => throw new IllegalArgumentException(error.toString), identity),
      serviceName = "Manicure",
      categoryName = "Nails",
      masterName = "Jane Doe",
      locationName = "Downtown Studio",
      address = "1 Main St",
      location = GeoPoint(BigDecimal("40.7128"), BigDecimal("74.0059")),
      lat = BigDecimal("40.7128"),
      lon = BigDecimal("74.0059"),
      priceFrom = BigDecimal("20.5"),
      priceTo = BigDecimal("40.75"),
      durationMin = 45,
      enumAttributes = Map(
        "nail_coating_type" -> "gel_polish",
        "nail_service_type" -> "manicure",
      ),
      booleanAttributes = Map("with_design" -> true),
      intAttributes = Map("session_count" -> 1),
      bigDecimalAttributes = Map("deposit_amount" -> BigDecimal("10.25")),
      allText = "manicure nails gel polish",
      serviceText = "manicure nails",
      attributeText = "gel polish",
      providerText = "jane doe downtown studio",
      locationText = "downtown studio 1 main st nails",
  )

  /** A real typed source graph for resource tests. The materialized fixture
    * above remains a compact scripted baseline; this graph is passed through
    * BeautyQVariantMaterializer when a second generation must differ by data. */
  val snapshot: BeautyQSearchSnapshot = {
    val category = Category(document.categoryId, document.categoryCode, Category.rootCategoryId, 1, document.categoryName)
    val service = Service(document.serviceId, document.serviceCode, document.categoryId, document.serviceName)
    val schema = ServiceVariantSchema.fromItems(
      document.serviceId,
      Vector(
        ServiceVariantSchemaItem(AttributeDefinition.SessionCount, required = false),
        ServiceVariantSchemaItem(AttributeDefinition.DepositAmount, required = false),
        ServiceVariantSchemaItem(AttributeDefinition.WithRemoval, required = false),
        ServiceVariantSchemaItem(AttributeDefinition.NailCoatingTypeAttribute, required = false),
      ),
    )
    val master = Master(document.masterId, document.masterName)
    val location = MasterLocation(document.masterLocationId, document.masterId, document.locationName, document.address, document.lat, document.lon)
    val offer = MasterServiceOffer(document.masterServiceOfferId, document.masterId, document.serviceId)
    val variant = MasterServiceOfferVariant.make(
      document.variantId,
      document.masterServiceOfferId,
      document.masterLocationId,
      document.priceFrom,
      document.priceTo,
      document.durationMin,
      MasterServiceOfferVariantAttributes(
        intValues = AttributeMap.empty.updated(AttributeDefinition.SessionCount, document.intAttributes.getOrElse("session_count", throw new IllegalStateException("expected session_count fixture"))),
        bigDecimalValues = AttributeMap.empty.updated(AttributeDefinition.DepositAmount, document.bigDecimalAttributes.getOrElse("deposit_amount", throw new IllegalStateException("expected deposit_amount fixture"))),
        enumValues = AttributeMap.empty.updated(AttributeDefinition.NailCoatingTypeAttribute, NailCoatingType.GelPolish),
        booleanValues = AttributeMap.empty.updated(AttributeDefinition.WithRemoval, true),
      ),
    ).getOrElse(throw new IllegalStateException("expected typed resource fixture variant"))
    BeautyQSearchSnapshot(
      categories = Vector(category),
      services = Vector(service),
      serviceVariantSchemas = Vector(schema),
      masters = Vector(master),
      masterLocations = Vector(location),
      masterServiceOffers = Vector(offer),
      masterServiceOfferVariants = Vector(variant),
    )
  }

  private val emptySnapshot = BeautyQSearchSnapshot(Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

  val materialized: MaterializedBeautyQVariantDocuments =
    MaterializedSearchDocuments(
      VersionedSnapshot(emptySnapshot, ContentFingerprint("content-fp-1"), Some(SourceRevision("rev-1")), Instant.parse("2024-01-01T00:00:00Z")),
      Vector(document),
      ProjectedDocumentsFingerprint("projected-fp-1"),
      ProjectionFormatVersion("beautyq-projection-v1"),
    )
}
