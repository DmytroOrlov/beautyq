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
      enumAttributes = Map("nail_coating_type" -> "gel_polish"),
      booleanAttributes = Map("with_design" -> true),
      intAttributes = Map("session_count" -> 1),
      bigDecimalAttributes = Map("deposit_amount" -> BigDecimal("10.25")),
      allText = "manicure nails gel polish",
      serviceText = "manicure nails",
      attributeText = "gel polish",
      providerText = "jane doe downtown studio",
      locationText = "downtown studio 1 main st nails",
    )

  private val emptySnapshot = BeautyQSearchSnapshot(Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

  val materialized: MaterializedBeautyQVariantDocuments =
    MaterializedSearchDocuments(
      VersionedSnapshot(emptySnapshot, ContentFingerprint("content-fp-1"), Some(SourceRevision("rev-1")), Instant.parse("2024-01-01T00:00:00Z")),
      Vector(document),
      ProjectedDocumentsFingerprint("projected-fp-1"),
      ProjectionFormatVersion("beautyq-projection-v1"),
    )
}
